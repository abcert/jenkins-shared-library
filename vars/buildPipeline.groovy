def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

pipeline {
    // run on jenkins nodes tha has java 8 label
    agent { label 'master' }
    // global env variables
    environment {
        EMAIL_RECIPIENTS = 'certification82@gmail.com'
    }
    
    tools {
            maven 'maven'
            jdk 'jdk'
    }

    options {
        disableConcurrentBuilds()
        skipStagesAfterUnstable()
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '2'))
    }


    stages {

        stage('Build with unit testing') {
            steps {

                //input message: '', parameters: [choice(choices: ['develop', 'feature', 'master'], description: 'select branch based on env.', name: 'SELECT_BRANCH_NAME')]

                //echo "${SELECT_BRANCH_NAME}"
                // Run the maven build
                script {
                    // Get the Maven tool.
                    // ** NOTE: This 'M3' Maven tool must be configured
                    // **       in the global configuration.

                    checkout scm

                    echo 'Pulling...' + env.BRANCH_NAME
                    def mvnHome = tool 'maven'
                    if (isUnix()) {
                        def targetVersion = getDevVersion()
                        print 'target build version...'
                        print targetVersion
                        //change directory for javamodule where we have pom
                        dir("javamodule"){
                            sh "ls -lrt"
                            //sh "'${mvnHome}/bin/mvn' -Dintegration-tests.skip=true -Dbuild.number=${targetVersion} clean package"
                            sh 'mvn clean package -DskipTests=true'
                            def pom = readMavenPom file: 'pom.xml'
                            // get the current development version
                            developmentArtifactVersion = "${pom.version}-${targetVersion}"
                            print pom.version
                            // execute the unit testing and collect the reports
                            //junit '**//*target/surefire-reports/TEST-*.xml'
                            //archive 'target*//*.jar'
                        }
                    } else {
                        bat(/"${mvnHome}\bin\mvn" -Dintegration-tests.skip=true clean package/)
                        def pom = readMavenPom file: 'pom.xml'
                        print pom.version
                        junit '**//*target/surefire-reports/TEST-*.xml'
                        archive 'target*//*.jar'
                    }
                }

            }
        }
        stage('Integration tests') {
            // Run integration test
            steps {
                script {
                    def mvnHome = tool 'maven'
                    if (isUnix()) {
                        // just to trigger the integration test without unit testing
                        //sh "'${mvnHome}/bin/mvn'  verify -Dunit-tests.skip=true"
                        echo "Integration test"
                    } else {
                        bat(/"${mvnHome}\bin\mvn" verify -Dunit-tests.skip=true/)
                    }

                }
                // cucumber reports collection
                //cucumber buildStatus: null, fileIncludePattern: '**/cucumber.json', jsonReportDirectory: 'target', sortingMethod: 'ALPHABETICAL'
            }
        }

        /*
        stage('Sonar scan execution') {
            // Run the sonar scan
            steps {
                script {
                    def mvnHome = tool 'maven'
                    withSonarQubeEnv {
                     
                        sh "'${mvnHome}/bin/mvn'  verify sonar:sonar -Dintegration-tests.skip=true -Dmaven.test.failure.ignore=true"
                    }
                }
            }
        }
        // waiting for sonar results based into the configured web hook in Sonar server which push the status back to jenkins
        stage('Sonar scan result check') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    retry(3) {
                        script {
                            def qg = waitForQualityGate()
                            if (qg.status != 'OK') {
                                error "Pipeline aborted due to quality gate failure: ${qg.status}"
                            }
                        }
                    }
                }
            }
        }
        */

        stage('Development deploy approval and deployment') {
            steps {
                script {
                    if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                        timeout(time: 3, unit: 'DAYS') {
                            // you can use the commented line if u have specific user group who CAN ONLY approve
                            //input message:'Approve deployment?', submitter: 'it-ops'
                            input message: 'Approve deployment?'
                        }
                        timeout(time: 2, unit: 'MINUTES') {
                            //
                            if (developmentArtifactVersion != null && !developmentArtifactVersion.isEmpty()) {
                                // replace it with your application name or make it easily loaded from pom.xml
                                def jarName = "application-${developmentArtifactVersion}.jar"
                                echo "the application is deploying ${jarName}"
                                // NOTE : CREATE your deployemnt JOB, where it can take parameters whoch is the jar name to fetch from jenkins workspace
                                //build job: 'ApplicationToDev', parameters: [[$class: 'StringParameterValue', name: 'jarName', value: jarName]]
                                echo 'the application is deployed !'
                            } else {
                                error 'the application is not  deployed as development version is null!'
                            }

                        }
                    }
                }
            }
        }
        stage('DEV sanity check') {
            steps {
                // give some time till the deployment is done, so we wait 45 seconds
                sleep(5)
                script {
                    if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                        timeout(time: 1, unit: 'MINUTES') {
                            script {
                                def mvnHome = tool 'maven'
                                //NOTE : if u change the sanity test class name , change it here as well
                                //sh "'${mvnHome}/bin/mvn' -Dtest=ApplicationSanityCheck_ITT surefire:test"
                            }

                        }
                    }
                }
            }
        }
        stage('Release and publish artifact') {
            when {
                // check if branch is master
                branch 'master'
            }
            steps {
                // create the release version then create a tage with it , then push to nexus releases the released jar
                script {
                    def mvnHome = tool 'maven'
                    if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                        echo "${currentBuild.result}"
                        /*
                        def v = getReleaseVersion()
                        releasedVersion = v;
                        if (v) {
                            echo "Building version ${v} - so released version is ${releasedVersion}"
                        }
                        */

                        // jenkins user credentials ID which is transparent to the user and password change
                        /*
                        sshagent(['0000000-3b5a-454e-a8e6-c6b6114d36000']) {
                            sh "git tag -f v${v}"
                            sh "git push -f --tags"
                        }
                         */
                        //sh "'${mvnHome}/bin/mvn' -Dmaven.test.skip=true  versions:set  -DgenerateBackupPoms=false -DnewVersion=${v}"
                        //sh "'${mvnHome}/bin/mvn' -Dmaven.test.skip=true clean deploy"

                    } else {
                        error "Release is not possible. as build is not successful"
                    }
                }
            }
        }
        stage('Deploy to Acceptance') {
            when {
                // check if branch is master
                branch 'master'
            }
            steps {
                script {
                    if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                        timeout(time: 3, unit: 'MINUTES') {
                            //input message:'Approve deployment?', submitter: 'it-ops'
                            input message: 'Approve deployment to UAT?'
                        }
                        /*
                        timeout(time: 3, unit: 'MINUTES') {
                            //  deployment job which will take the relasesed version
                            if (releasedVersion != null && !releasedVersion.isEmpty()) {
                                // make the applciation name for the jar configurable
                                def jarName = "application-${releasedVersion}.jar"
                                echo "the application is deploying ${jarName}"
                                // NOTE : DO NOT FORGET to create your UAT deployment jar , check Job AlertManagerToUAT in Jenkins for reference
                                // the deployemnt should be based into Nexus repo
                                //build job: 'AApplicationToACC', parameters: [[$class: 'StringParameterValue', name: 'jarName', value: jarName], [$class: 'StringParameterValue', name: 'appVersion', value: releasedVersion]]
                                echo 'the application is deployed !'
                            } else {
                                error 'the application is not  deployed as released version is null!'
                            }

                        }

                         */
                    }
                }
            }
        }
        stage('ACC E2E tests') {
            when {
                // check if branch is master
                branch 'master'
            }
            steps {

                echo "ACC E2E tests stage"
                // give some time till the deployment is done, so we wait 45 seconds
                //sleep(5)
                //script {
                    //if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                        //timeout(time: 1, unit: 'MINUTES') {

                          //  script {
                            //    def mvnHome = tool 'maven'
                                // NOTE : if you change the test class name change it here as well
                                //sh "'${mvnHome}/bin/mvn' -Dtest=ApplicationE2E surefire:test"
                           // }

                       // }
                  //  }
                }
            }
        }
    }

    post {
        // Always runs. And it runs before any of the other post conditions.
        always {
            // Let's wipe out the workspace before we finish!
            deleteDir()
        }
        success {
            echo "Successful"
            //sendEmail("Successful");
            notifyBuild("Successful")
        }
        unstable {
            echo "Unstable"
            //sendEmail("Unstable");
            notifyBuild("Successful")
        }
        failure {
            echo "Failed"
            //sendEmail("Failed");
            notifyBuild("Successful")
        }
    }
}


// if you want parallel execution , check below :
/* stage('Quality Gate(Integration Tests and Sonar Scan)') {
           // Run the maven build
           steps {
               parallel(
                       IntegrationTest: {
                           script {
                               def mvnHome = tool 'Maven 3.3.9'
                               if (isUnix()) {
                                   sh "'${mvnHome}/bin/mvn'  verify -Dunit-tests.skip=true"
                               } else {
                                   bat(/"${mvnHome}\bin\mvn" verify -Dunit-tests.skip=true/)
                               }

                           }
                       },
                       SonarCheck: {
                           script {
                               def mvnHome = tool 'Maven 3.3.9'
                               withSonarQubeEnv {
                                   // sh "'${mvnHome}/bin/mvn'  verify sonar:sonar -Dsonar.host.url=http://bicsjava.bc/sonar/ -Dmaven.test.failure.ignore=true"
                                   sh "'${mvnHome}/bin/mvn'  verify sonar:sonar -Dmaven.test.failure.ignore=true"
                               }
                           }
                       },
                       failFast: true)
           }
       }*/
//}

def developmentArtifactVersion = ''
def releasedVersion = ''
// get change log to be send over the mail

def getChangeString() {
    MAX_MSG_LEN = 100
    def changeString = ""

    echo "Gathering SCM changes"
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            truncated_msg = entry.msg.take(MAX_MSG_LEN)
            changeString += " - ${truncated_msg} [${entry.author}]\n"
        }
    }

    if (!changeString) {
        changeString = " - No new changes"
    }
    return changeString
}

def sendEmail(status) {
    mail(
            to: "$EMAIL_RECIPIENTS",
            subject: "Build $BUILD_NUMBER - " + status + " (${currentBuild.fullDisplayName})",
            //body: "Changes:\n " +  "\n\n Check console output at: $BUILD_URL/console" + "\n")
            body: "Changes:\n " + getChangeString() + "\n\n Check console output at: $BUILD_URL/console" + "\n")
}

def getDevVersion() {
    def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def versionNumber;
    if (gitCommit == null) {
        versionNumber = env.BUILD_NUMBER;
    } else {
        versionNumber = gitCommit.take(8);
    }
    print 'build  versions...'
    print versionNumber
    return versionNumber
}

def getReleaseVersion() {
    def pom = readMavenPom file: 'pom.xml'
    def gitCommit = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    def versionNumber;
    if (gitCommit == null) {
        versionNumber = env.BUILD_NUMBER;
    } else {
        versionNumber = gitCommit.take(8);
    }
    return pom.version.replace("-SNAPSHOT", ".${versionNumber}")
}

def getCurrentBranch () {
    return sh (
            script: 'git rev-parse --abbrev-ref HEAD',
            returnStdout: true
    ).trim()
}

def getChangeAuthorName() {
    return sh(returnStdout: true, script: "git show -s --pretty=%an").trim()
}

def getChangeAuthorEmail() {
    return sh(returnStdout: true, script: "git show -s --pretty=%ae").trim()
}

def getShortCommitHash() {
    return sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
}

def getChangeSet() {
    return sh(returnStdout: true, script: 'git diff-tree --no-commit-id --name-status -r HEAD').trim()
}

def getChangeLog() {
    return sh(returnStdout: true, script: "git log --date=short --pretty=format:'%ad %aN <%ae> %n%n%x09* %s%d%n%b'").trim()
}

def notifyBuild(String buildStatus = 'STARTED') {
    // build status of null means successful
    buildStatus = buildStatus ?: 'SUCCESS'

    def branchName = getCurrentBranch()
    def shortCommitHash = getShortCommitHash()
    def changeAuthorName = getChangeAuthorName()
    def changeAuthorEmail = getChangeAuthorEmail()
    def changeSet = getChangeSet()
    def changeLog = getChangeLog()

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: '${env.JOB_NAME} [${env.BUILD_NUMBER}]'" + branchName + ", " + shortCommitHash
    def summary = "Started: Name:: ${env.JOB_NAME} \n " +
            "Build Number: ${env.BUILD_NUMBER} \n " +
            "Build URL: ${env.BUILD_URL} \n " +
            "Short Commit Hash: " + shortCommitHash + " \n " +
            "Branch Name: " + branchName + " \n " +
            "Change Author: " + changeAuthorName + " \n " +
            "Change Author Email: " + changeAuthorEmail + " \n " +
            "Change Set: " + changeSet + " \n " +
            "Change Log: " + changeLog

    echo "${summary}"

    if (buildStatus == 'STARTED') {
        color = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESS') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else {
        color = 'RED'
        colorCode = '#FF0000'
    }

    mail(
            to: "$EMAIL_RECIPIENTS",
            subject: subject,
            body: summary)

    // Send notifications
    //hipchatSend(color: color, notify: true, message: summary, token: "${env.HIPCHAT_TOKEN}",
    //    failOnError: true, room: "${env.HIPCHAT_ROOM}", sendAs: 'Jenkins', textFormat: true)
    /*
    if (buildStatus == 'FAILURE') {
        echo "Email Sent"
        //emailext attachLog: true, body: summary, compressLog: true, recipientProviders: [brokenTestsSuspects(), brokenBuildSuspects(), culprits()], replyTo: 'noreply@yourdomain.com', subject: subject, to: 'mpatel@yourdomain.com'
    }
     */
}