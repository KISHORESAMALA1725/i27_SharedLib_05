package com.i27academy.k8s;

class K8s {
    def jenkins
    k8s(jenkins) {
        this.jenkins = jenkins      
    }

    def auth_login(clusterName, zone, projectID) {
        jenkins.sh """
        echo "Entering K8S authentication/login methods"
        gcloud compute instances list
        echo "Create config file for env"
        gcloud container clusters get-credentials i27-${clusterName}} --${zone} us-central1-c --project ${projectID}
        """
    }
}

