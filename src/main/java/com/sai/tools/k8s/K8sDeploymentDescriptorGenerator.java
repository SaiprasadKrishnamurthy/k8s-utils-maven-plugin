package com.sai.tools.k8s;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static com.sai.tools.k8s.PropertiesNormalizer.*;
import static java.util.stream.Collectors.joining;
// TODO extract as a separate maven plugin.

/**
 * Generates the deployment descriptors for Kubernetes.
 *
 * @author Sai.
 */
public class K8sDeploymentDescriptorGenerator {
    private static final String SPRING_PROPERTY_FILES_BASE_DIR = "src/main/resources";

    private static String SCOPED_VARIABLE_ARTIFACT_ID = "artifactId";
    private static String SCOPED_VARIABLE_PROPERTIES = "properties";
    private static String SCOPED_VARIABLE_SERVICE_NAME = "serviceName";
    private static String SCOPED_VARIABLE_SERVICE_VERSION = "version";
    private static String SCOPED_VARIABLE_CONFIG_MAP_TEMPLATE_NAME = "configMapTemplateName";
    private static String SCOPED_VARIABLE_FULLY_QUALIFIED_DOCKER_IMAGE_NAME = "fullyQualifiedDockerImageName";
    private static String SCOPED_VARIABLE_REPLICAS = "replicas";
    private static String SCOPED_VARIABLE_VOLUME_MOUNT = "volumeMount";


    public static void generate(final String projectArtifactId, final String projectVersion, final String fullyQualifiedDockerImageName, final int replicas, final String volumeMount) throws Exception {
        Map<String, Properties> environmentsAndProperties = new HashMap<>();
        Map<String, TreeMap<Object, Object>> mergedPropertiesPerEnvironment = new HashMap<>();
        Files.walk(Paths.get(SPRING_PROPERTY_FILES_BASE_DIR))
                .filter(path -> path.toFile().isFile())
                .filter(path -> !path.toString().startsWith("."))
                .filter(path -> path.toString().endsWith(".properties"))
                .forEach(path -> {
                    try {
                        File file = path.toFile();
                        String fileName = file.getName();
                        String environment = !fileName.contains("application-") ? "" : fileName
                                .replace("application-", "")
                                .replace(".properties", "");
                        Properties properties = new Properties();
                        properties.load(new FileInputStream(file));
                        injectDefaultScopedProperties(properties);
                        environmentsAndProperties.put(environment, toEnvironmentVariableFriendlyProperties(properties));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
        Properties base = environmentsAndProperties.get("");
        environmentsAndProperties
                .keySet()
                .stream()
                .filter(key -> key.length() > 0)
                .forEach(env -> {
                    TreeMap<Object, Object> merged = merge(base, environmentsAndProperties.get(env));
                    addScopedProperties(projectArtifactId, projectVersion, fullyQualifiedDockerImageName, replicas, volumeMount, merged);
                    mergedPropertiesPerEnvironment.put(env, merged);
                });
        if (mergedPropertiesPerEnvironment.isEmpty()) {
            TreeMap<Object, Object> merged = merge(environmentsAndProperties.get(""));
            addScopedProperties(projectArtifactId, projectVersion, fullyQualifiedDockerImageName, replicas, volumeMount, merged);
            mergedPropertiesPerEnvironment.put("default", merged);
        }
        // Generate yml files using the templates.
        String configMapTemplate = IOUtils.toString(K8sDeploymentDescriptorGenerator.class.getClassLoader().getResourceAsStream("configmap-template.yml"), Charset.defaultCharset());
        String deployTemplate = IOUtils.toString(K8sDeploymentDescriptorGenerator.class.getClassLoader().getResourceAsStream("service-deployment-template.yml"), Charset.defaultCharset());
        Set<String> deploymentTemplateVariableNames = extractVariableNames(deployTemplate);
        Set<String> configMapTemplateVariableNames = extractVariableNames(configMapTemplate);
        Map<String, String> generatedDeploymentYmls = generate(projectArtifactId, projectVersion, mergedPropertiesPerEnvironment, deployTemplate, deploymentTemplateVariableNames);
        Map<String, String> generatedConfigMapYmls = generate(projectArtifactId, projectVersion, mergedPropertiesPerEnvironment, configMapTemplate, configMapTemplateVariableNames);
        Map<String, String> generatedEncryptedProperties = generateProperties(mergedPropertiesPerEnvironment);
        writeFile(projectArtifactId + "-" + "configmap", generatedConfigMapYmls, ".yml");
        writeFile(projectArtifactId + "-" + "deployment", generatedDeploymentYmls, ".yml");
        //writeFile(projectArtifactId + "-" + "encrypted", generatedEncryptedProperties, ".properties");
        // Replace the variables in the shell scripts.
        generateScripts(projectArtifactId, mergedPropertiesPerEnvironment, "deploy_configs.sh", IOUtils.toString(K8sDeploymentDescriptorGenerator.class.getClassLoader().getResourceAsStream("deploy_configs.sh"), Charset.defaultCharset()));
        generateScripts(projectArtifactId, mergedPropertiesPerEnvironment, "deploy_service.sh", IOUtils.toString(K8sDeploymentDescriptorGenerator.class.getClassLoader().getResourceAsStream("deploy_service.sh"), Charset.defaultCharset()));
        generateScripts(projectArtifactId, mergedPropertiesPerEnvironment, "logs.sh", IOUtils.toString(K8sDeploymentDescriptorGenerator.class.getClassLoader().getResourceAsStream("logs.sh"), Charset.defaultCharset()));
    }

    private static void addScopedProperties(String projectArtifactId, String projectVersion, String fullyQualifiedDockerImageName, int replicas, String volumeMount, TreeMap<Object, Object> merged) {
        merged.put("build_version", projectVersion);
        merged.put(SCOPED_VARIABLE_ARTIFACT_ID, projectArtifactId);
        merged.put(SCOPED_VARIABLE_SERVICE_NAME, projectArtifactId);
        merged.put(SCOPED_VARIABLE_SERVICE_VERSION, projectVersion);
        merged.put(SCOPED_VARIABLE_FULLY_QUALIFIED_DOCKER_IMAGE_NAME, fullyQualifiedDockerImageName);
        merged.put(SCOPED_VARIABLE_VOLUME_MOUNT, volumeMount);
        if (!merged.containsKey("server.port")) {
            merged.put("server.port", "8080"); // default spring boot.
        }
        merged.put(SCOPED_VARIABLE_REPLICAS, replicas);
    }

    private static void generateScripts(String projectArtifactId, Map<String, TreeMap<Object, Object>> mergedPropertiesPerEnvironment, String fileName, String contents) {
        try {
            Set<String> variables = extractVariableNames(contents);
            TreeMap<Object, Object> propertiesFromAnyOneEnvironment = mergedPropertiesPerEnvironment.entrySet().iterator().next().getValue();
            for (String variable : variables) {
                contents = contents.replace("${" + variable + "}", extractProperty(propertiesFromAnyOneEnvironment, variable) + "");
            }
            FileUtils.write(Paths.get("target", "k8s", projectArtifactId + "-" + fileName).toFile(), contents, Charset.defaultCharset());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void injectDefaultScopedProperties(final Properties properties) {
        // Inject Random.
        properties.put("random", UUID.randomUUID().toString());
    }

    private static void writeFile(final String fileNamePrefix, final Map<String, String> generatedConfigMapYmls, String fileExtension) {
        generatedConfigMapYmls.forEach((k, v) -> {
            try {
                FileUtils.write(Paths.get("target", "k8s", fileNamePrefix + "-" + k + fileExtension).toFile(), v, Charset.defaultCharset());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }


    private static Map<String, String> generate(final String projectArtifactId,
                                                final String projectVersion,
                                                final Map<String, TreeMap<Object, Object>> mergedPropertiesPerEnvironment,
                                                final String deployTemplate,
                                                final Set<String> vars) {
        Map<String, String> generatedFileContents = new HashMap<>();
        mergedPropertiesPerEnvironment.forEach((env, props) -> {
            String generated = deployTemplate;
            Map<Object, Object> contextVars = new HashMap<>();
            String propertiesDump = props.keySet().stream().map(k -> "  " + k + ": \"" + props.get(k) + "\"").collect(joining("\n"));
            contextVars.put(SCOPED_VARIABLE_PROPERTIES, propertiesDump);
            contextVars.put(SCOPED_VARIABLE_CONFIG_MAP_TEMPLATE_NAME, projectArtifactId + "-config-" + projectVersion.toLowerCase());
            contextVars.putAll(props);
            for (String variable : vars) {
                generated = generated.replace("${" + variable + "}", extractProperty(contextVars, variable) + "");
            }
            generatedFileContents.put(env, generated);
        });
        return generatedFileContents;
    }

    private static Map<String, String> generateProperties(final Map<String, TreeMap<Object, Object>> mergedPropertiesPerEnvironment) {
        Map<String, String> generatedFileContents = new HashMap<>();
        mergedPropertiesPerEnvironment.entrySet().stream().forEach(map -> {
            String propertiesDump = map.getValue().keySet().stream()
                    .filter(k -> map.getValue().get(k).toString().startsWith("ENC("))
                    .map(k -> k + "=" + map.getValue().get(k)).collect(joining("\n"));
            generatedFileContents.put(map.getKey(), propertiesDump.toString());
        });
        return generatedFileContents;
    }

    private static Object extractProperty(final Map<Object, Object> contextVars, final String variable) {
        Object o = contextVars.get(variable);
        if (o == null) {
            return contextVars.get(toEnvironmentVariableFriendlyString(variable));
        }
        return o;
    }

    private static TreeMap<Object, Object> merge(final Properties... properties) {
        return Stream.of(properties)
                .collect(TreeMap::new, Map::putAll, Map::putAll);
    }
}
