package vars


def call() {
    node {
        stage('Checkout') {
            checkout scm
        }
        def p = pipelineCfg()

        if (p.runTests == true) {
            echo "test"
        }


        if (env.BRANCH_NAME == 'master' && p.deployUponTestSuccess == true) {
            docker.image(p.deployToolImage).inside {
                stage('Deploy') {
                    sh "echo ${p.deployCommand} ${p.deployEnvironment}"
                }
            }
        }
    }
}