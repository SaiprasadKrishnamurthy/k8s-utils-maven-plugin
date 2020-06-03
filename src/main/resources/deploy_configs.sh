#!/bin/sh
if [ -z "$1" ]; then
    echo -e "\nPlease call '$0 <environment>' to run this command!\n"
    exit 1
fi
ENV=$1
CONFIGMAP_FILE=${serviceName}-configmap-$ENV.yml

do
kubectl delete -f $CONFIGMAP_FILE
done

