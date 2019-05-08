def getVersion(type = null) {
  /* if type is undefined we get VERSION from repo root */
  def path = "${env.WORKSPACE}/VERSION"
  if (type != null) {
    path = "${env.WORKSPACE}/${type}/VERSION"
  }
  return readFile(path).trim()
}

def getToolVersion(name) {
  def version = sh(
    returnStdout: true,
    script: "${env.WORKSPACE}/scripts/toolversion ${name}"
  ).trim()
  return version
}

def nix_impure_sh(cmd) {
  _nix_sh(cmd, true)
}

def nix_sh(cmd) {
  _nix_sh(cmd, false)
}

def _nix_sh(cmd, forceImpure) {
  def isPure = !forceImpure && env.TARGET_OS != 'windows' && env.TARGET_OS != 'ios'
  def pureFlag = isPure ? '--pure --keep LOCALE_ARCHIVE_2_27 --keep REALM_DISABLE_ANALYTICS --keep STATUS_RELEASE_STORE_FILE --keep STATUS_RELEASE_STORE_PASSWORD --keep STATUS_RELEASE_KEY_ALIAS --keep STATUS_RELEASE_KEY_PASSWORD --keep GPG_PASS_OUTER --keep GPG_PASS_INNER --keep KEYCHAIN_PASS --keep VERBOSE_LEVEL' : ''

  sh """
    set +x
    . ~/.nix-profile/etc/profile.d/nix.sh
    set -x
    nix-shell --argstr target-os \'${env.TARGET_OS}\' \\
              ${pureFlag} --run \'${cmd}\' \\
              \'${env.WORKSPACE}/shell.nix\'
  """
}

def nix_fastlane_sh(cmd) {
  def isPure = env.TARGET_OS != 'ios'
  def pureFlag = isPure ? '--pure --keep LANG --keep LANGUAGE --keep LC_ALL --keep DIAWI_TOKEN --keep DIAWI_IPA --keep APK_PATH --keep GOOGLE_PLAY_JSON_KEY --keep SAUCE_LABS_NAME --keep SAUCE_USERNAME --keep SAUCE_ACCESS_KEY --keep FASTLANE_DISABLE_COLORS --keep FASTLANE_APPLE_ID --keep FASTLANE_PASSWORD --keep KEYCHAIN_PASSWORD --keep MATCH_PASSWORD --keep REALM_DISABLE_ANALYTICS --keep STATUS_RELEASE_STORE_FILE --keep GRADLE_USER_HOME' : ''

  sh """
    set +x
    . ~/.nix-profile/etc/profile.d/nix.sh
    set -x
    nix-shell --argstr target-os \'${env.TARGET_OS}\' \\
              ${pureFlag} --run \'\$FASTLANE_SCRIPT ${cmd}\' \\
              \'${env.WORKSPACE}/shell.nix\'
  """
}

def nix_bundle_sh(cmd) {
  def pureFlag = '--keep LANG --keep LANGUAGE --keep LC_ALL --keep DIAWI_TOKEN --keep DIAWI_IPA --keep APK_PATH --keep GOOGLE_PLAY_JSON_KEY --keep SAUCE_LABS_NAME --keep SAUCE_USERNAME --keep SAUCE_ACCESS_KEY --keep FASTLANE_DISABLE_COLORS --keep FASTLANE_APPLE_ID --keep FASTLANE_PASSWORD --keep KEYCHAIN_PASSWORD --keep MATCH_PASSWORD --keep REALM_DISABLE_ANALYTICS --keep STATUS_RELEASE_STORE_FILE --keep GRADLE_USER_HOME'

  sh """
    set +x
    . ~/.nix-profile/etc/profile.d/nix.sh
    set -x
    nix-shell --argstr target-os \'${env.TARGET_OS}\' \\
              ${pureFlag} --run \'${cmd}\' \\
              \'${env.WORKSPACE}/shell.nix\'
  """
}

def branchName() {
  return env.GIT_BRANCH.replaceAll(/.*origin\//, '')
}

def parentOrCurrentBuild() {
  def c = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
  if (c == null) { return currentBuild }
  return c.getUpstreamRun()
}

def timestamp() {
  /* we use parent if available to make timestmaps consistent */
  def now = new Date(parentOrCurrentBuild().timeInMillis)
  return now.format('yyMMdd-HHmmss', TimeZone.getTimeZone('UTC'))
}

def gitCommit() {
  return GIT_COMMIT.take(6)
}

def pkgFilename(type, ext) {
  return "StatusIm-${timestamp()}-${gitCommit()}-${type}.${ext}"
}

def doGitRebase() {
  /* rebasing on relases defeats the point of having a release branch */
  if (params.BUILD_TYPE == 'release') {
    println 'Skipping rebase due to release build...'
    return
  }
  sh 'git status'
  sh 'git fetch --force origin develop:develop'
  try {
    sh 'git rebase develop'
  } catch (e) {
    sh 'git rebase --abort'
    throw e
  }
}

def genBuildNumber() {
  def number = sh(
    returnStdout: true,
    script: "./scripts/gen_build_no.sh"
  ).trim()
  println "Build Number: ${number}"
  return number
}

def getDirPath(path) {
  return path.tokenize('/')[0..-2].join('/')
}

def getFilename(path) {
  return path.tokenize('/')[-1]
}

def getEnv(build, envvar) {
  return build.getBuildVariables().get(envvar)
}

def buildDuration() {
  def duration = currentBuild.durationString
  return '~' + duration.take(duration.lastIndexOf(' and counting'))
}

def pkgFind(glob) {
  def fullGlob =  "pkg/*${glob}"
  def found = findFiles(glob: fullGlob)
  if (found.size() == 0) {
    sh 'ls -l pkg/'
    error("File not found via glob: ${fullGlob} ${found.size()}")
  }
  return found[0].path
}

def installJSDeps(platform) {
  def attempt = 1
  def maxAttempts = 10
  def installed = false
  /* prepare environment for specific platform build */
  nix_sh "scripts/prepare-for-platform.sh ${platform}"
  while (!installed && attempt <= maxAttempts) {
    println "#${attempt} attempt to install npm deps"
    nix_sh 'yarn install --frozen-lockfile'
    installed = fileExists('node_modules/web3/index.js')
    attemp = attempt + 1
  }
}

def uploadArtifact(path) {
  /* defaults for upload */
  def domain = 'ams3.digitaloceanspaces.com'
  def bucket = 'status-im'
  /* There's so many PR builds we need a separate bucket */
  if (getBuildType() == 'pr') {
    bucket = 'status-im-prs'
  }
  /* WARNING: s3cmd can't guess APK MIME content-type */
  def customOpts = ''
  if (path.endsWith('apk')) {
    customOpts += "--mime-type='application/vnd.android.package-archive'"
  }
  /* We also need credentials for the upload */
  withCredentials([usernamePassword(
    credentialsId: 'digital-ocean-access-keys',
    usernameVariable: 'DO_ACCESS_KEY',
    passwordVariable: 'DO_SECRET_KEY'
  )]) {
    sh """
      s3cmd \\
        --acl-public \\
        ${customOpts} \\
        --host='${domain}' \\
        --host-bucket='%(bucket)s.${domain}' \\
        --access_key=${DO_ACCESS_KEY} \\
        --secret_key=${DO_SECRET_KEY} \\
        put ${path} s3://${bucket}/
    """
  }
  return "https://${bucket}.${domain}/${getFilename(path)}"
}

def getBuildType() {
  def jobName = env.JOB_NAME
  if (jobName.contains('e2e')) {
      return 'e2e'
  }
  if (jobName.startsWith('status-react/prs')) {
      return 'pr'
  }
  if (jobName.startsWith('status-react/nightly')) {
      return 'nightly'
  }
  if (jobName.startsWith('status-react/release')) {
      return 'release'
  }
  return params.BUILD_TYPE
}

def getParentRunEnv(name) {
  def c = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
  if (c == null) { return null }
  return c.getUpstreamRun().getEnvironment()[name]
}

def changeId() {
  /* CHANGE_ID can be provided via the build parameters or from parent */
  def changeId = env.CHANGE_ID
  changeId = params.CHANGE_ID ? params.CHANGE_ID : changeId
  changeId = getParentRunEnv('CHANGE_ID') ? getParentRunEnv('CHANGE_ID') : changeId
  if (!changeId) {
    println('This build is not related to a PR, CHANGE_ID missing.')
    println('GitHub notification impossible, skipping...')
    return null
  }
  return changeId
}

def updateEnv(type) {
  def envFile = "${env.WORKSPACE}/.env"
  /* select .env based on type of build */
  def selectedEnv = '.env.jenkins'
  switch (type) {
    case 'nightly': selectedEnv = '.env.nightly'; break
    case 'release': selectedEnv = '.env.prod';    break
    case 'e2e':     selectedEnv = '.env.e2e';     break
  }
  sh "cp ${selectedEnv} .env"
  /* find a list of .env settings to check for them in params */
  def envContents = readFile(envFile)
  def envLines = envContents.split()
  def envVars = envLines.collect { it.split('=').first() }
  /* for each var available in params modify the .env file */
  envVars.each { var ->
    if (params.get(var)) { /* var exists in params and is not empty */
      println("Changing setting: ${var}=${params.get(var)}")
      sh "sed -i'.bkp' 's/${var}=.*/${var}=${params.get(var)}/' ${envFile}"
    }
  }
  /* show contents for debugging purposes */
  sh "cat ${envFile}"
}

return this
