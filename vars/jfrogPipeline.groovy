import com.i27academy.builds.Docker;
import com.i27academy.k8s.K8s;

def call(Map pipelineParams) {
    Docker docker = new Docker(this)
    K8s k8s = new K8s(this)  
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
            // DOCKER DEPLOYMENT
            DEV_HOST_PORT = "${pipelineParams.devHostPort}"
            TEST_HOST_PORT = "${pipelineParams.testHostPort}"
            STAGE_HOST_PORT = "${pipelineParams.stageHostPort}"
            PROD_HOST_PORT = "${pipelineParams.prodHostPort}"
            CONT_PORT = "${pipelineParams.contPort}"

            // READ-POMfile METHOD
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()

            //DOCKER VM INFO
            // DOCKER_HUB = "docker.io/kishoresamala84"
            // DOCKER_CREDS = credentials('kishoresamala84_docker_creds')
            // DOCKER_VM = '34.21.68.255'

            // JFROG CREDS 

            JFROG_DOCKER_REGISTRY = 'i27academy.jfrog.io'
            JFROG_DOCKER_REPO_NAME = 'cart-docker'
            JFROG_CREDS = credentials('JFROG_CREDS')

            //K8S DETAILS
            DEV_CLUSTER_NAME = "i27-cluster"
            DEV_CLUSTER_ZONE = "us-central1-c"
            DEV_PROJECT_ID = "shanwika-456212"

            //K8S YAML_FILES
            K8S_DEV_FILE = 'k8s_dev.yaml'
            K8S_TEST_FILE = 'k8s_test.yaml'
            K8S_STAGE_FILE = 'k8s_stage.yaml'
            K8S_PROD_FILE =  'k8s_prod.yaml'

            //K8S NAMESPACES 
            DEV_NAMESPACE = 'cart-dev-ns'
            TEST_NAMESPACE = 'cart-test-ns'
            STAGE_NAMESPACE = 'cart-stage-ns'
            PROD_NAMESPACE = 'cart-prod-ns'
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
                        docker_image = "${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}","${env.DEV_CLUSTER_ZONE}","${env.DEV_PROJECT_ID}")
                        // dockerDeploy('dev', "${env.DEV_HOST_PORT}", "${env.CONT_PORT}").call()
                        k8s.k8sdeploy("${env.K8S_DEV_FILE}",docker_image,"${env.DEV_NAMESPACE}")
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
            sh "docker pull ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
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
                sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
                sh "docker login -u ${env.JFROG_CREDS_USR} -p ${env.JFROG_CREDS_PSW} i27academy.jfrog.io"
                sh "docker push ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
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
                                // THIS UPPER LINE CAN BE IGNORED FOR JFROG SINCE WE ARE NOT DEPLOYTING TO DOCKER REGISTRY ANY MORE //
            }
        }      
    }
}