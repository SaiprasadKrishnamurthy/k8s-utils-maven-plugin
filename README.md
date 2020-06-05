## Kubernetes Deployment Generator for Spring Boot apps.

This maven plugin acts as a bridge between your spring-boot app and kubernetes and generates the necessary 
Kubernetes deployment descriptors, config maps and utility shell scrips simply by extracting the
application.properties and your maven metadata.

## Usage in your pom.

```
        <plugin>
                <groupId>com.github.saiprasadkrishnamurthy</groupId>
                <artifactId>k8s-utils-maven-plugin</artifactId>
                <version>1.1</version>
                <executions>
                    <execution>
                        <id>generate-kubernetes-deployment-descriptors</id>
                        <goals>
                            <goal>generate-deployment</goal>
                        </goals>
                        <configuration>
                            <dockerImageNamespace>dockerhub.com</dockerImageNamespace>
                            <dockerImagePrefix>appname</dockerImagePrefix>
                            <replicas>1</replicas>
                            <volumeMount>/mymount</volumeMount>
                        </configuration>
                    </execution>
                </executions>
         </plugin>
``` 

Pass the relevant parameters above in your <configuration> section.
This plugin gets kicked off during the prepare-package phase by default.

Once this is run, you'll find `target/k8s` directory created.

Let's assume if your spring boot application has the following structure of application properties.

```
src
    main
        resources
            application.properties
            application-local.properties
            application-dev.properties
            application-test.properties
            application-staging.properties
            application-prod.properties
```

For every spring environment profile (local, dev, test, staging, prod), a pair of Kubernetes files would be created
* **<application_name>-deployment-<profile> yml**
* **<application_name>-configmap-<profile>.yml**

Assume your project's artifact id is: `user-search-service`
```
target
     k8s
        user-search-service-deployment-local.yml
        user-search-service-configmap-local.yml
        user-search-service-deployment-dev.yml
        user-search-service-configmap-dev.yml
        user-search-service-deployment-test.yml
        user-search-service-configmap-test.yml
        user-search-service-deployment-staging.yml
        user-search-service-configmap-staging.yml
        user-search-service-deployment-prod.yml
        user-search-service-configmap-prod.yml
```

In addition to that, 3 utility shell scripts would be created:

* **deploy_configs.sh** - Script that helps you to deploy a specific environment specific config map for this service using kubectl. 
* **deploy_service.sh** - Script that helps you to deploy a specific environment specific deployment for this service  using kubectl. 
* **logs.sh** - Script that helps you to tail the logs.

 