// slack plugin color codes
// https://github.com/jenkinsci/slack-plugin/blob/e568a98e4fbe93937f12d9ff22dad282c7e3d374/src/main/resources/jenkins/plugins/slack/workflow/SlackSendStep/help-color.html

String cause() {
  currentBuild.build().getAction(CauseAction.class).getShortDescription()
}

String duration() {
  def dur = currentBuild.build().getDurationString()

  // the build hasn't technically ended while the pipeline script is still
  // executing, so jenkins is appending "and counting" to the duration string
  def m = dur =~ /(.*) and counting/
  def durClean =  m[0][1]

  "after ${durClean}"
}

String slackMessage(String detail) {
  "${env.JOB_NAME} - #${env.BUILD_NUMBER} ${detail} (<${env.BUILD_URL}|Open>) (<${env.BUILD_URL}/console|Console>)"
}

String slackStartMessage() {
  slackMessage(cause())
}

String slackSuccessMessage() {
  slackMessage("Success ${duration()}")
}

String slackFailureMessage() {
  slackMessage("Failure ${duration()}")
}

String slackAbortedMessage() {
  slackMessage("Aborted ${duration()}")
}

def started() {
  slackSend color: 'good', message: slackStartMessage()
}

def success() {
  slackSend color: 'good', message: slackSuccessMessage()
}

def aborted() {
  slackSend color: 'warning', message: slackAbortedMessage()
}

def failure() {
  slackSend color: 'danger', message: slackFailureMessage()
}

return this;
