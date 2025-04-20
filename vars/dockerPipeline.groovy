import com.i27academy.builds.Docker;

def call(Map pipelineParams) {
    Docker docker = new Docker(this)
        pipeline {
            agent {
                label 'k8s-slave'
            }

            tools {
                maven 'maven-3.8.8'
                jdk 'jdk-17'
            }

            parameters {
                choice(name: 'buildOnly', choices: 'no\nyes', description: 'Will do BUILD-ONLY')
                choice(name: 'scanOnly', choices: 'no\nyes', description: 'Will perform SCAN-ONLY')
                choice(name: 'dockerBuildAndPush', choices: 'no\nyes', description: 'Docker build and push')
                choice(name: 'deploytodev', choices: 'no\nyes', description: 'Deploying to Dev')
                choice(name: 'deploytotest', choices: 'no\nyes', description: 'Deploying to Test')
                choice(name: 'deploytostage', choices: 'no\nyes', description: 'Deploying to Stage')
                choice(name: 'deploytoprod', choices: 'no\nyes', description: 'Deploying to Prod')        
            }

            environment {
                APPLICATION_NAME = "${pipelineParams.appName}"
                DEV_HOST_PORT = "${pipelineParams.devHostPort}"
                TEST_HOST_PORT = "${pipelineParams.testHostPort}"
                STAGE_HOST_PORT = "${pipelineParams.stageHostPort}"
                PROD_HOST_PORT = "${pipelineParams.prodHostPort}"
                CONT_PORT = "${pipelineParams.contPort}"
                POM_VERSION = readMavenPom().getVersion()
                POM_PACKAGING = readMavenPom().getPackaging()
                DOCKER_HUB = "docker.io/kishoresamala84"
                DOCKER_CREDS = credentials('kishoresamala84_docker_creds')
                DOCKER_VM = '34.21.68.255'
            }

            stages {
                stage ('BUILD_STAGE') {
                    when {
                        anyOf {
                            expression {
                                params.scanOnly == 'yes'
                                params.buildOnly =='yes'
                            }
                        }
                    }            
                    steps {
                        script{
                            docker.buildApp("${env.APPLICATION_NAME}")
                        }
                    }
                }        

                stage ('SONARQUBE_STAGE') {
                    when {
                        anyOf {
                            expression {
                                params.scanOnly == 'yes'
                                params.buildOnly =='yes'
                            }
                        }
                    } 
                    steps {
                        echo "****************** Starting Sonar Scans with Quality Gates ******************"
                        withSonarQubeEnv('sonarqube'){
                            script {
                                sh """
                                mvn sonar:sonar \
                                -Dsonar.projectKey=i27-"${env.APPLICATION_NAME}"-05 \
                                -Dsonar.host.url=http://35.188.226.250:9000 \
                                -Dsonar.login=sqa_7d01297a6e4c6d1d7f64e2f1137dcbc2df213ec4    
                                """                    
                            }
                        }
                        timeout (time: 2, unit: "MINUTES" ) {
                            waitForQualityGate abortPipeline: true
                        }                
                    }
                }

                stage ('BUILD_FORMAT_STAGE') {
                    steps {
                        script {
                            sh """
                            echo "Source JAR_FORMAT i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING}"
                            echo "Target JAR_FORMAT i27-${env.APPLICATION_NAME}-${BRANCH_NAME}-${currentBuild.number}.${env.POM_PACKAGING}"
                            """
                        }
                    }
                }

                stage ('DOCKER_BUILD_AND_PUSH') {
                    when {
                        expression {
                            params.dockerBuildAndPush == 'yes'
                        }
                    }
                    steps {
                        script {
                            dockerBuildAndPush().call()                    
                        }
                    }
                }

                stage ('DEPLOY_TO_DEV') {
                    when {
                        expression {
                            params.deploytodev == 'yes'
                        }
                    }
                    steps {
                        script {
                            imageValidation().call()
                            dockerDeploy('dev', "${env.DEV_HOST_PORT}", "${env.CONT_PORT}").call()
                        }
                    }
                }

                stage ('DEPLOY_TO_TEST') {
                    when {
                        expression {
                            params.deploytotest == 'yes'
                        }
                    }
                    steps {
                        script {
                            imageValidation().call()
                            dockerDeploy('test', "${env.TEST_HOST_PORT}", "${env.CONT_PORT}").call()
                        }
                    }
                }

                stage ('DEPLOY_TO_STAGE') {
                    when {
                        expression {
                            params.deploytostage == 'yes'
                        }
                    }
                    steps {
                        script {
                            imageValidation().call()
                            dockerDeploy('stage', "${env.STAGE_HOST_PORT}", "${env.CONT_PORT}").call()
                        }
                    }
                }

                stage ('DEPLOY_TO_PROD') {
                    when {
                        expression {
                            params.deploytoprod == 'yes'
                        }
                    }
                    steps {
                        script {
                            imageValidation().call()
                            dockerDeploy('prod', "${env.PROD_HOST_PORT}", "${env.CONT_PORT}").call()
                        }
                    }
                }
            }

            post {
                success{
                    script{                
                        def subject = "Success !!! Job is:=> [${env.JOB_NAME}] <<>> Build # is :=> [${env.BUILD_NUMBER}] <<>> status is :=> [${currentBuild.currentResult}]"
                        def body =  "Build Number:=> ${env.BUILD_NUMBER} \n\n" +
                                "status:=> ${currentBuild.currentResult} \n\n" +
                                "Job URL:=> ${env.BUILD_URL}"
                        sendEmailNotification('kishorecloud.1725@gmail.com', subject, body)               
                    }            
                }

                failure{
                    script{                
                        def subject = "failure <<>> Job is:=> [${env.JOB_NAME}] <<>> Build # is :=> [${env.BUILD_NUMBER}] <<>> status is :=> [${currentBuild.currentResult}]"
                        def body =  "Build Number:=> ${env.BUILD_NUMBER} \n\n" +
                                "status:=> ${currentBuild.currentResult} \n\n" +
                                "Job URL:=> ${env.BUILD_URL}"
                        sendEmailNotification('kishorecloud.1725@gmail.com', subject, body)               
                    }              
                }
            }    
        }   
}    

 
 /// methods ///

 def sendEmailNotification(String recipient, String subject, String body) {
    mail (
        to: recipient,
        subject: subject,
        body: body
    )
}

def buildApp() {
    return {
            sh "mvn clean package -DskipTest=true"
            archiveArtifacts 'target/*.jar'
    }
}

def imageValidation() {
    return {
        echo "Trying to pull the image"
        try {
            sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}/${GIT_COMMIT}"
            echo " Image pulled successfully"          
            }
        catch(Exception e) {
            println("***** OOPS, the docker images with this tag is not available in the repo, so creating the image********")
            buildApp().call()
            dockerBuildAndPush().call()
        }
    }
}

def dockerBuildAndPush() {
    return {
            script {
                sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
                sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
                sh "docker login -u ${env.DOCKER_CREDS_USR} -p ${env.DOCKER_CREDS_PSW}"
                sh "docker push ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            }         
    }
}

def dockerDeploy(envDeploy, hostPort, contPort) {
    return {
    echo "********* Deploying to dev Environment **************"
        withCredentials([usernamePassword(credentialsId: 'john_docker_vm_creds', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
            script {
                try {
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@'$DOCKER_VM' \"docker stop ${env.APPLICATION_NAME}-$envDeploy\""
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@'$DOCKER_VM' \"docker rm ${env.APPLICATION_NAME}-$envDeploy\""
                }
                catch (err){
                    echo "Caught Error: $err"
                }
                sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@'$DOCKER_VM' \"docker container run -dit -p $hostPort:$contPort --name ${env.APPLICATION_NAME}-$envDeploy ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}\""
            }
        }      
    }
}
