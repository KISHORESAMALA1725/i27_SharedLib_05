import com.i27academy.builds.Docker;
import com.i27academy.k8s.K8s;

def call(Map pipelineParams) {
     
    // An instance of the class Docker is created
    Docker docker = new Docker(this)
    K8s k8s = new K8s(this)
    pipeline {
        agent {
            label 'k8s-slave'
        }

        //  { choice(name: 'CHOICES', choices: ['one', 'two', 'three'], description: '') }
        parameters {
            choice(name: 'scanOnly',
                choices: 'no\nyes',
                description: "This will scan the application"
            )
            choice(name: 'buildOnly',
                choices: 'no\nyes',
                description: 'This will Only build the application'
            )
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'This will trigger the app build, docker build and docker push'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This will Deploy the application to Dev env'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This will Deploy the application to Test env'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'This will Deploy the application to Stage env'
            )
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'This will Deploy the application to Prod env'
            )
        }
        // tools configured in jenkins-master
        tools {
            maven 'Maven-3.8.8'
            jdk 'JDK-17'
        }
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
        
            // the below are container ports
            CONT_PORT = "${pipelineParams.contPort}"
            POM_VERSION = readMavenPom().getVersion() 
            POM_PACKAGING = readMavenPom().getPackaging()
            // DOCKER_HUB = "docker.io/i27devopsb5"
            //DOCKER_CREDS = credentials('dockerhub_creds') // username and password

            // these are kubernetes details
            DEV_CLUSTER_NAME = "i27-cluster"
            DEV_CLUSTER_ZONE = "us-central1-a"
            DEV_PROJECT_ID = "plenary-magpie-445512-c3"

            // K8s File names
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TST_FILE = "k8s_tst.yaml"
            K8S_STG_FILE = "k8s_stg.yaml"
            K8S_PRD_FILE = "k8s_prd.yaml"

            // Namespace definiton
            DEV_NAMESPACE = "cart-dev-ns"
            TST_NAMESPACE = "cart-tst-ns"
            STG_NAMESPACE = "cart-stg-ns"
            PRD_NAMESPACE = "cart-prd-ns"

            // Jfrog Details
            JFROG_DOCKER_REGISTRY = "devopsb5.jfrog.io"
            JFROG_DOCKER_REPO_NAME = "cart-docker"
            JFROG_CREDS = credentials('JFROG_CREDS') // credentials to connect to my private JFROG
        }

        stages {
            stage ('Build'){
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'
                        }
                    }
                }
                // This is Where Build for Eureka application happens
                steps {
                    script{
                        // Shared library implementation methos
                        docker.buildApp("${env.APPLICATION_NAME}")
                    }
                    // echo "Building ${env.APPLICATION_NAME} Application"
                    // sh 'mvn clean package -DskipTests=true'
                    // // mvn clean package -DskipTests=true
                    // // mvn clean package -Dmaven.test.skip=true
                    // archive 'target/*.jar'
                }
            }
            stage ('SonarQube'){
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'
                            params.scanOnly == 'yes'
                        }
                    }
                }
                steps {
                    // COde Quality needs to be implemented in this stage
                    // Before we execute or write the code, make sure sonarqube-scanner plugin is installed.
                    // sonar details are ben configured in the Manage Jenkins > system 
                    echo "****************** Starting Sonar Scans with Quality Gates ******************"
                    withSonarQubeEnv('SonarQube') { // SonarQube is the name we configured in Manage Jenkins > System > Sonarqube , it should match exactly, 
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=i27-eureka \
                                -Dsonar.host.url=http://35.188.56.142:9000 \
                                -Dsonar.login=sqa_577b1c8f0339303219f309fb46bb5f730ce1cf65
                        """
                    }
                    timeout (time: 2, unit: 'MINUTES') { //NANOSECONDS, SECONDS, MINUTES, HOURS, DAYS
                        waitForQualityGate abortPipeline: true
                    }
    
                }
            }
            stage ('Docker Build Push') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        dockerBuildAndPush().call()
                    }
                }
            }
            stage ('Deploy to Dev Env'){
                when {
                    expression {
                        params.deployToDev == 'yes'
                    }
                }
                steps {
                    script {

                        // this will create the docker image name
                        def docker_image = "${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        
                        // this will login to the kubernetes cluster
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")

                        // this will validate the image and pull the image if it is not available
                        imageValidation().call()

                        // Deploying to Kubernetes cluster in dev namespace 
                        k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                        
                    }
    //(fileName, docker_image, namespace)
                }
                // a mail should trigger based on the status
                // Jenkins url should be sent as an a email.
            }
            stage ('Deploy to Test Env'){
                when {
                    expression {
                        params.deployToTest == 'yes'
                    }
                }
                steps {
                    script {
                        imageValidation().call()
                        dockerDeploy('dev', "${env.TST_HOST_PORT}", "${env.CONT_PORT}").call()
                    }
                }
            }
            stage ('Deploy to Stage Env'){
                when {

                allOf {
                    anyOf {
                        expression {
                            params.deployToStage == 'yes'
                        }
                    }
                    anyOf {
                        branch 'release/*'
                        tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP" // v1.2.3 is the correct one, v123 is the wrong one
                    }
                }
                }
                steps {
                    script {
                        imageValidation().call()
                        dockerDeploy('dev', "${env.STG__HOST_PORT}", "${env.CONT_PORT}").call()
                        //dockerDeploy('stg', '7232', '8232').call()
                    }
                }
            }
            stage ('Deploy to Prod Env'){
                // when {
                //     expression {
                //         params.deployToProd == 'yes'
                //     }
                // }
                when {
                    allOf {
                        anyOf {
                            expression {
                                params.deployToProd == 'yes'
                            }
                        }
                        anyOf {
                            tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}", comparator: "REGEXP" // v1.2.3 is the correct one, v123 is the wrong one
                        }
                    }
                }
                steps {
                    timeout(time: 300, unit: 'SECONDS'){ // SECONDS, MINUTES, HOURs
                        input message: "Deploying to ${env.APPLICATION_NAME} to production ??", ok:'yes', submitter: 'sivasre,i27academy'
                    }

                    script {
                        dockerDeploy('dev', "${env.PROD__HOST_PORT}", "${env.CONT_PORT}").call()
                        //dockerDeploy('prd', '8232', '8232').call()
                    }
                }
            }
        }
    }
}
// This Jenkins file is for Eureka Deployment



//App Building
def buildApp(){
    return {
        echo "Building ${env.APPLICATION_NAME} Application"
        sh 'mvn clean package -DskipTests=true'
    }
}


// imageValidation
def imageValidation() {
    return {
        println("******** Attemmpting to Pull the Docker Images *********")
        try {
            sh "docker pull ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("************* Image is Pulled Succesfully ***********")
        }
        catch(Exception e) {
            println("***** OOPS, the docker images with this tag is not available in the repo, so creating the image********")
            buildApp().call()
            dockerBuildAndPush().call()
        }

    }
}

// Method for Docker build and push 
def dockerBuildAndPush(){
    return {
        echo "****************** Building Docker image ******************"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd/"
        // devopsb5.jfrog.io/cart-docker/eureka:GIT_COMMIT
        echo "****************** Login to Jfrog Registry ******************"
        sh "docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW} devopsb5.jfrog.io"
        echo "****************** Push Image to JFROG Registry ******************"
        sh "docker push ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
    }
}

// Method for Docker Deployment as containers in different env's
def dockerDeploy(envDeploy, hostPort, contPort){
    return {
        echo "****************** Deploying to $envDeploy Environment  ******************"
        withCredentials([usernamePassword(credentialsId: 'john_docker_vm_passwd', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                // some block
                // we will communicate to the server
                script {
                    try {
                        // Stop the container 
                        sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@$dev_ip \"docker stop ${env.APPLICATION_NAME}-$envDeploy \""

                        // Remove the Container
                        sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@$dev_ip \"docker rm ${env.APPLICATION_NAME}-$envDeploy \""

                    }
                    catch(err){
                        echo "Error Caught: $err"
                    }
                    // Command/syntax to use sshpass
                    //$ sshpass -p !4u2tryhack ssh -o StrictHostKeyChecking=no username@host.example.com
                    // Create container 
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no '$USERNAME'@$dev_ip \"docker container run -dit -p $hostPort:$contPort --name ${env.APPLICATION_NAME}-$envDeploy ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} \""
                }
        }   
    }
}


// For eureka lets use the below port numbers
// Container port will be 8232 only, only host port changes
// dev: HostPort = 5232
// tst: HostPort = 6232
// stg: HostPort = 7232
// prod: HostPort = 8232