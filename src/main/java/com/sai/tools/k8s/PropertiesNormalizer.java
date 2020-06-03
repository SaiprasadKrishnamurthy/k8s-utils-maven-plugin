package com.sai.tools.k8s;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Spring Boot properties (usually contains '.' as a delimiter). However, the '.' delimiter
 * is not accepted as an environment variable when supplied through an external source
 * (for example: Kubernetes ConfigMap). Therefore, this class would replace all the '.' to '_'
 * so as to make both Spring and OS-Environments happy.
 * The good thing is spring boot's configuration mechanism would automatically use the '_' versions
 * instead of '.' versions if both were present. This is because by convention it gives a higher precendence to
 * environment variables than the ones bundled in the classpath (inside the jar file).
 *
 * @author Sai.
 */
public final class PropertiesNormalizer {

    private static final Pattern REGEX_EXTRACT_VARIABLE_NAMES_FROM_TEMPLATE = Pattern.compile("\\{(.*?)\\}");

    private PropertiesNormalizer() {
    }

    static String toEnvironmentVariableFriendlyString(final String key) {
        return key.replace(".", "_").replace("-", "");
    }

    static Properties toEnvironmentVariableFriendlyProperties(final Properties properties) {
        Properties normalised = new Properties();
        properties.forEach((key, value) -> {
            if (!ignoreProp(key)) {
                String normalisedKey = toEnvironmentVariableFriendlyString(key.toString());
                Set<String> actualVariables = extractVariableNames(value.toString());
                if (!actualVariables.isEmpty()) {
                    String normalisedValue = value.toString();
                    for (String actualVariable : actualVariables) {
                        String normalizedValue = toEnvironmentVariableFriendlyString(actualVariable);
                        normalisedValue = normalisedValue.replace(actualVariable, normalizedValue);
                    }
                    normalised.put(normalisedKey, normalisedValue);
                } else {
                    normalised.put(normalisedKey, value);
                }
            } else {
                normalised.put(key, value);
            }
        });
        return normalised;
    }

    private static boolean ignoreProp(final Object key) {
        return key.toString().equalsIgnoreCase("random");
    }

    public static Set<String> extractVariableNames(final String value) {
        Matcher matchPattern = REGEX_EXTRACT_VARIABLE_NAMES_FROM_TEMPLATE.matcher(value);
        Set<String> vars = new HashSet<>();
        while (matchPattern.find()) {
            vars.add(matchPattern.group(1));
        }
        return vars;
    }
}
