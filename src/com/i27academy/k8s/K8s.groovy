package com.i27academy.k8s;

class K8s {
// write all the methods here     
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins      
    }

    def auth_login(clusterName, zone, projectID) {
        jenkins.sh """
            echo "Entering K8S authentication/login methods"
            gcloud compute instances list
            echo "Create config file for env"
            gcloud container clusters get-credentials $clusterName --zone $zone  --project $projectID
            kubectl get nodes
        """
    }
}