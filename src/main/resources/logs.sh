#!/bin/sh
POD=$(kubectl get pod -l app=${serviceName} -o jsonpath="{.items[0].metadata.name}")
kubectl logs -f $POD
