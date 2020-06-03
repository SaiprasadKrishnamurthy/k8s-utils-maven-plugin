#!/bin/sh
if [ -z "$1" ]; then
    echo -e "\nPlease call '$0 <environment>' to run this command!\n"
    exit 1
fi
ENV=$1
DEPLOYMENT_FILE=${serviceName}-deployment-$ENV.yml
kubectl delete -f $DEPLOYMENT_FILE


kubectl create -f $DEPLOYMENT_FILE

kubectl get pods

