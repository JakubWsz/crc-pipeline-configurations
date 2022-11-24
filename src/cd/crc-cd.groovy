package cd

PROJECT_GIT_URL = "https://github.com/JakubWsz/carRentalCompany3.git"
pipeline {
    agent any

    tools {
        maven "M3"
        dockerTool "docker"
    }

    stages {
        // checkout project
        stage('checkout') {
            steps {
                git "$PROJECT_GIT_URL"
                script {
                    REPOSITORY_VERSION = sh(
                            script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout",
                            returnStdout: true
                    ).trim()
                }
            }
        }
        // build and archive project
        stage('build & archive') {
            steps {
                sh "mvn -Dmaven.test.failure.ignore=true clean package"
                archiveArtifacts 'target/*.jar'
            }
        }
        // build and tag image
        stage('build & push image') {
            steps {
                sh "docker build . --tag jakubwsz/crc-management-crud:${REPOSITORY_VERSION} -f Dockerfile"
                withCredentials([usernamePassword(credentialsId: 'dockerhub', passwordVariable: 'DOCKER_PASSWORD',
                        usernameVariable: 'DOCKER_USERNAME')]) {
                    sh "docker login -u='${DOCKER_USERNAME}' -p='${DOCKER_PASSWORD}' "
                }
                sh "docker push jakubwsz/crc-management-crud:${REPOSITORY_VERSION}"
            }
        }
        // update vps
        stage('update vps') {
            steps {
                sshagent(['vps-dev']) {
                    sh "docker-compose up -d --no-deps jakubwsz/crc-management-crud:${REPOSITORY_VERSION}"
                }
            }
        }
        // increment version
        stage('increment version') {
            steps {
                sh 'mvn -B release:prepare release:perform'
                sh "git commit -m 'increment version'"
                sh "git push"
            }
        }
    }
}
