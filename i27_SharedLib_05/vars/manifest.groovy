import com.i27academy.builds.Docker;
import com.i27academy.k8s.K8s;


def call(Map pipelineParams){
    // An instance of the class called calculator is created
    Docker docker = new Docker(this)   
    K8s k8s = new K8s(this) 

    // This Jenkinsfile is for Eureka Deployment 

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            string(name: 'NAMESPACE_NAME', description: "Enter the name of the namespace, you want to create")
        }
        environment {
            // these are kubernetes details
            DEV_CLUSTER_NAME = "i27-cluster"
            DEV_CLUSTER_ZONE = "us-central1-a"
            DEV_PROJECT_ID = "plenary-magpie-445512-c3"
            
            APPLICATION_NAME = "${pipelineParams.appName}"
            SONAR_URL = "http://35.196.148.247:9000"
            SONAR_TOKEN = credentials('sonar_creds')
            DOCKER_HUB = "docker.io/i27k8s10"
            GKE_DEV_CLUSTER_NAME = "cart-dev-ns"
            GKE_DEV_ZONE = "us-central1-c"
            GKE_DEV_PROJECT = "quantum-weft-420714"
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TST_FILE = "k8s_tst.yaml"
            K8S_STAGE_FILE = "k8s_stage.yaml"
            K8S_PROD_FILE = "k8s_prod.yaml"
            DEV_NAMESPACE = "cart-dev-ns"
            TST_NAMESPACE = "cart-tst-ns"
            STAGE_NAMESPACE = "cart-stage-ns"
            PROD_NAMESPACE = "cart-prod-ns"
            HELM_PATH = "${WORKSPACE}/i27-shared-lib/chart"
            DEV_ENV = "dev"
            TST_ENV = "tst"
            STAGE_ENV = "stage"
            PROD_ENV = "prod"
            JFROG_DOCKER_REGISTRY = "flipkarrt.jfrog.io"
            JFROG_DOCKER_REPO_NAME = "cont-images-docker"
            JFROG_CREDS = credentials('JFROG_CREDS')
        }
        stages {
            stage ('Checkout'){
                steps {
                    println("Checkout: Cloning git repo for i27Shared Library *************")
                    script {
                        k8s.gitClone()
                    }
                }
            }
            stage ('Authentication') {
                steps {
                    echo "Executing in GCP Cloud authentication stage"
                    script{
                        k8s.auth_login("${env.DEV_CLUSTER_NAME}", "${env.DEV_CLUSTER_ZONE}", "${env.DEV_PROJECT_ID}")
                    }
                }
            }
            stage ('Create K8S Namespace'){
                steps {
                    script {
                        k8s.namespace_creation("${params.NAMESPACE_NAME}")
                    }
                }
            }
        }
        post {
            always {
                echo "Cleaning up the i27-shared-lib directory"
                script {
                    def sharedLibPath = "${WORKSPACE}/i27-shared-lib"
                    if (fileExists(sharedLibPath)) {
                        echo "Deleting the shared library directory: ${sharedLibPath}"
                        sh "rm -rf ${sharedLibPath}"
                    } else {
                        echo "No shared library directory found to clean up."
                    }
                }
            }
        }
    }
}
