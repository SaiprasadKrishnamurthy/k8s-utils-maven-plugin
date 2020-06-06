package com.sai.tools.k8s;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * An example Maven Mojo that generates Kubernetes config map files from a hierarchy of Spring boot properties files.
 */
@Mojo(name = "generate-deployment", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class GenerateDeploymentsMojo extends AbstractMojo {

    @Parameter(property = "project")
    private MavenProject project;

    @Parameter(property = "dockerImageNamespace")
    private String dockerImageNamespace;

    @Parameter(property = "dockerImagePrefix")
    private String dockerImagePrefix;

    @Parameter(property = "replicas", defaultValue = "1")
    private int replicas;

    @Parameter(property = "volumeMount", defaultValue = "/tmp")
    private String volumeMount;

    @Parameter(property = "skip")
    private boolean skip;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!skip) {
            try {
                String groupId = project.getGroupId();
                String artifactId = project.getArtifactId();
                String version = project.getVersion();
                String dockerFullyQualifiedName = dockerImageNamespace + "/" + dockerImagePrefix + "/" + artifactId;
                if (StringUtils.isBlank(dockerImagePrefix)) {
                    dockerFullyQualifiedName = dockerImageNamespace + "/" + artifactId;
                }
                getLog().info(String.format(" Generating Kubernetes Deployment Files for:  %s:%s:%s", groupId, artifactId, version));
                K8sDeploymentDescriptorGenerator.generate(artifactId, version, dockerFullyQualifiedName, replicas, volumeMount);
            } catch (Exception ex) {
                getLog().error(ex);
                throw new RuntimeException(ex);
            }
        } else {
            getLog().warn(" Kubernetes Deployment Files generation skipped ");
        }
    }
}