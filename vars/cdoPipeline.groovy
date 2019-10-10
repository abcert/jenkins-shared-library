
def call(body) {
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    pipeline {
        agent {
            //label 'builder-backend'
            label 'master'
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
            stage('build') {
                steps {
                    //notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)
                    deleteDir()
                    checkout scm
                    sh """
            ls -lrt
          """

                 dir("javamodule"){
                     sh """
                    ls -lrt
                    """

                     sh 'mvn clean package -DskipTests=true'
                 }


                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }
            stage('qa: static code analysis') {
                steps {
                    //notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)
                    sh """
            ls -lrt
          """
                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }
            stage('qa: unit & integration tests') {
                steps {
                    //notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)
                    sh """
            ls -lrt
          """
                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }
            stage('approval: dev') {
                agent none
                when {
                    not {
                        branch 'master'
                    }
                }
                steps {
                    script {
                        timeout(5) {
                            input "Deploy to dev?"
                        }
                    }
                }
            }
            stage('deploy: dev') {
                when {
                    allOf {
                        not {
                            branch 'master'
                        }
                        expression { return currentBuild.result == null || currentBuild.result == 'SUCCESS' }
                    }
                }
                steps {
                    //notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)
                    echo "deployment to dev env successful"
                    //releaseHeroku(pipelineParams.developmentRepositoryUrl, 'development')
                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }

            stage('approval: stg') {
                agent none
                when {
                    allOf {
                        branch 'release'
                    }
                }
                steps {
                    script {
                        timeout(5) {
                            input "Deploy to stg?"
                        }
                    }
                }
            }

            stage('deploy: stg') {
                when {
                    allOf {
                        branch 'release'
                        expression { return currentBuild.result == null || currentBuild.result == 'SUCCESS' }
                    }
                }
                steps {
                    //notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)
                    echo "deployment to stg succesful"
                    //releaseHeroku(pipelineParams.stagingRepositoryUrl, 'staging')
                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }

            stage('approval: prod') {
                agent none
                when {
                    allOf {
                        branch 'master'
                    }
                }
                steps {
                    script {
                        timeout(5) {
                            input "Deploy to production?"
                        }
                    }
                }
            }
            stage('deploy: prod') {
                when {
                    allOf {
                        branch 'master'
                        expression { return currentBuild.result == null || currentBuild.result == 'SUCCESS' }
                    }
                }
                steps {
                    //notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)
                    echo "Yeeeho deployment to prod successful"
                    //releaseHeroku(pipelineParams.productionRepositoryUrl, 'production')
                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }
            stage('publish api docs: prod') {
                when {
                    allOf {
                        branch 'master'
                        expression { return currentBuild.result == null || currentBuild.result == 'SUCCESS' }
                    }
                }
                steps {
                    notifyBitbucket('INPROGRESS', env.STAGE_NAME, env.STAGE_NAME)

                    sh """
            ls -lrt
          """
                }
                post {
                    success {
                        echo "success"
                        //notifySuccess(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                    failure {
                        echo "failure"
                        //notifyFailure(env.STAGE_NAME, pipelineParams.slackNotificationChannel)
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (currentBuild.result == null) {
                        currentBuild.result = 'SUCCESS'
                    }
                }
                //step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: gitAuthorEmail(), sendToIndividuals: true])
            }
        }
    }
}
