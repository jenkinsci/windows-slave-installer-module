pipeline {
    // Make sure that the tools we need are installed and on the path.
    tools {
        maven "mvn"
        jdk "jdk8"
    }
    
    agent {
        label "java"
    }
    
    // TODO: Copy-pasted from the Pipeline Model Definition example
    // Make sure we have GIT_COMMITTER_NAME and GIT_COMMITTER_EMAIL set due to machine weirdness.
    environment {
        GIT_COMMITTER_NAME = "jenkins"
        GIT_COMMITTER_EMAIL = "jenkins@jenkins.io"
    }
    
    stages {
        stage("build") {
            steps {
                sh 'mvn clean verify install -Dmaven.test.failure.ignore=true'
            }
        }
    }
    
    post {
        always {
            junit '*/target/surefire-reports/*.xml'
        }
        //TODO: is there any sense to publish Module jars?
    }
}
