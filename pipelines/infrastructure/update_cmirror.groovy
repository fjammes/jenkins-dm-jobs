def notify = null
node {
  dir('jenkins-dm-jobs') {
    checkout([
      $class: 'GitSCM',
      branches: scm.getBranches(),
      userRemoteConfigs: scm.getUserRemoteConfigs()
    ])
    notify = load 'pipelines/lib/notify.groovy'
  }
}

try {
  notify.started()

  def hub_repo = 'lsstsqre/cmirror'

  node('docker') {
    def image = docker.image("${hub_repo}:latest")

    stage('pull') {
      image.pull()
    }

    stage('mirror linux-64') {
      shColor '''
        mkdir -p local_mirror tmp
        chmod 777 local_mirror tmp
      '''.replaceFirst("\n","").stripIndent()

      runMirror(image.id, 'https://repo.continuum.io/pkgs/free/', 'linux-64')
    }

    stage('mirror osx-64') {
      runMirror(image.id, 'https://repo.continuum.io/pkgs/free/', 'osx-64')
    }

    stage('mirror miniconda') {
      shColor '''
        wget \
          --mirror \
          --continue \
          --no-parent \
          --no-host-directories \
          --progress=dot:giga \
          -R "*.exe" \
          -R "*ppc64le.sh" \
          -R "*armv7l.sh" \
          -R "*x86.sh" \
          https://repo.continuum.io/miniconda/
      '''.replaceFirst("\n","").stripIndent()
    }

    stage('push to s3') {
      withCredentials([[
        $class: 'UsernamePasswordMultiBinding',
        credentialsId: 'aws-cmirror-push',
        usernameVariable: 'AWS_ACCESS_KEY_ID',
        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
      ],
      [
        $class: 'StringBinding',
        credentialsId: 'cmirror-s3-bucket',
        variable: 'CMIRROR_S3_BUCKET'
      ]]) {
        shColor '''
          set -e
          # do not assume virtualenv is present
          pip install virtualenv
          virtualenv venv
          . venv/bin/activate
          pip install awscli

          aws s3 sync --delete ./local_mirror/ s3://$CMIRROR_S3_BUCKET/pkgs/free/
          aws s3 sync --delete ./miniconda/ s3://$CMIRROR_S3_BUCKET/miniconda/
        '''.replaceFirst("\n","").stripIndent()
      }
    } // stage('push to s3')
  } // node
} catch (e) {
  // If there was an exception thrown, the build failed
  currentBuild.result = "FAILED"
  throw e
} finally {
  echo "result: ${currentBuild.result}"
  switch(currentBuild.result) {
    case null:
    case 'SUCCESS':
      notify.success()
      break
    case 'ABORTED':
      notify.aborted()
      break
    case 'FAILURE':
      notify.failure()
      break
    default:
      notify.failure()
  }
}

def runMirror(String image, String upstream, String platform) {
  withEnv([
    "IMAGE=${image}",
    "UPSTREAM=${upstream}",
    "PLATFORM=${platform}",
  ]) {
    shColor '''
      docker run \
        -v $(pwd)/tmp:/tmp \
        -v $(pwd)/local_mirror:/local_mirror \
        "$IMAGE" \
        --upstream-channel "$UPSTREAM" \
        --target-directory /local_mirror \
        --platform "$PLATFORM" \
        -vvv
    '''.replaceFirst("\n","").stripIndent()
  }
}

def shColor(script) {
  wrap([$class: 'AnsiColorBuildWrapper']) {
    sh script
  }
}
