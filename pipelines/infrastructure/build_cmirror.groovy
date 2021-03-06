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

  def image = null
  def hub_repo = 'lsstsqre/cmirror'

  node('docker') {
    stage('checkout') {
      git([
        url: 'https://github.com/lsst-sqre/sandbox-cmirror',
        branch: 'master'
      ])
    }

    stage('build') {
      docker.image('centos:7').pull()

      dir('cmirror') {
        image = docker.build("${hub_repo}", '--no-cache .')
      }
    }

    stage('push') {
      docker.withRegistry('https://index.docker.io/v1/', 'dockerhub-sqreadmin') {
        image.push('latest')
      }
    }
  }
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
