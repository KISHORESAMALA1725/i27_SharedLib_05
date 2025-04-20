package com.i27academy.k8s;
class K8s {
// write all the methods here 
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }

    // Method to authenticate to kubernetes clusters
    def auth_login(clusterName, zone, projectID){
        jenkins.sh """
            echo "********************* Entering into kubernetes Authentication/login Method *********************"
            gcloud compute instances list
            echo "********************* Create the config file for the environment *********************"  
            gcloud container clusters get-credentials $clusterName --zone $zone --project $projectID
            kubectl get nodes 
        """
    }

    // Method to deploy the application. 
    def k8sdeploy(fileName, docker_image, namespace){
        jenkins.sh """
            echo "********************* Entering into kubernetes Deployment Method *********************"
            sed -i "s|DIT|${docker_image}|g" ./.cicd/${fileName}
            kubectl apply -f ./.cicd/${fileName} -n $namespace
        """
    }

    // Helm Deployments 
    def k8sHelmChartDeploy(appName, env, helmChartPath, imageTag, namespace){ 
        jenkins.sh """
            echo "********************* Entering into kubernetes Helm Deployment Method *********************"
            helm version
            echo "********************* Installing the Chart *********************"
            # Lets verify it the helm chart exists
            if helm list -n ${namespace} | grep -q ${appName}-${env}-chart; then
                echo "This Chart Exists"
                echo "Upgrading the Chart"
                helm upgrade ${appName}-${env}-chart -f .cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} ${helmChartPath} -n ${namespace}
            else
                echo "This Chart does not exist"
                echo "Installing the Chart"
                helm install ${appName}-${env}-chart -f .cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} ${helmChartPath} -n ${namespace}
            fi
        """
    }

    // CLone the Shared Library
    def gitClone(){
        jenkins.sh """
            echo "********************* Cloning the Shared Library *********************"
            git clone -b main https://github.com/i27devopsb5/i27-shared-lib.git
            echo "********************* Listing the files in the workspace*********************"
            ls -la 
            echo "********************* Listing the files in the shared library*********************"
            ls -la i27-shared-lib
        """
    }
    //  Namespace Creation
    def namespace_creation(namespace_name){
        jenkins.sh """#!/bin/bash
        # Script to create namespace, if doesnot exists
        #!/bin/bash
        #namespace_name="boutique"
        echo "Namespace Provided is ${namespace_name}"
        # Validate if the namespace exists
        if kubectl get ns "${namespace_name}" &> /dev/null ; then 
        echo "Your Namespace '${namespace_name}' exists!!!!!!"
        exit 0
        else
        echo "Your namespace '${namespace_name}' doesnot exists, so creating it!!!!!!"
        if kubectl create ns '${namespace_name}' &> /dev/null; then
          echo "Your namespace '${namespace_name}' has created succesfully"
          exit 0
        else 
          echo "Some error , failed to create '${namespace_name}'"
          exit 1
        fi
        fi
        """
    }
}

//            helm install ${appName}-${env}-chart -f values_dev.yaml --set image.tag={****} chartpath -n cart-dev-ns  

          //  helm upgrade 