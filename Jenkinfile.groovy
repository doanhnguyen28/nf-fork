pipeline {
    agent any
    tools {
        jdk 'jdk17'
        nodejs 'node16'
    }
    environment {
        SCANNER_HOME=tool 'sonar-scanner'
    }
    stages {
        stage('clean workspace') {
            steps{
                cleanWs()
            }
        }
        stage('Checkout from Git') {
            steps{
                git branch: 'main', url: 'https://gitlab.com/kiettt164/netflix.git'
            }
        }
        stage("Sonarqube Analysis ") {
            steps{
                withSonarQubeEnv('sonar-server') {
                    sh ''' $SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=Netflix \
                    -Dsonar.projectKey=Netflix '''
                }
            }
        }
        stage("quality gate") {
           steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-token' 
                }
            } 
        }
        stage('Install Dependencies') {
            steps {
                sh "npm install"
            }
        }
        stage('OWASP FS SCAN') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'DP-Check'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        stage('TRIVY FS SCAN') {
            steps {
                sh "trivy fs . > trivyfs.txt"
            }
        }
        stage("Docker Build & Push") {
            steps{
                script{
                   withDockerRegistry(credentialsId: 'docker', toolName: 'docker'){   
                       sh "docker build --build-arg TMDB_V3_API_KEY=0e67a64b279f94e96dfd333ef0e6095c -t netflix ."
                       sh "docker tag netflix kiettran164/netflix:latest "
                       sh "docker push kiettran164/netflix:latest "
                    }
                }
            }
        }
        stage("TRIVY"){
            steps{
                sh "trivy image kiettran164/netflix:latest > trivyimage.txt" 
            }
        }
        stage('Deploy to container'){
            steps{
                sh 'docker stop netflix'
                sh 'docker rm netflix'
                sh 'docker run -d --name netflix -p 8081:80 kiettran164/netflix:latest'
            }
        }
    }
    post {
      always {
          emailext attachLog: true,
            subject: "${currentBuild.result}",
            body: "Project: ${env.JOB_NAME}<br/>" +
                "Build Number: ${env.BUILD_NUMBER}<br/>" +
                "URL: ${env.BUILD_URL}<br/>",
            to: "trankiet164@gmail.com",
            attachmentsPattern: "trivyfs.txt,trivyimage.txt"
      }
    }
}
