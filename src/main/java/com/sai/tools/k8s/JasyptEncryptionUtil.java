package com.sai.tools.k8s;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.PropertiesConfigurationLayout;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.jasypt.util.text.BasicTextEncryptor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class JasyptEncryptionUtil {

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println(
					"Expected 3 arguments: 1.Path to property file 2.Project Artifact ID 3.Environment 4.Encryption Password");
			System.exit(1);
		}
		String path = args[0];
		String projectArtifactId = args[1];
		String envName = args[2];
		String password = args[3];
		String configYaml = projectArtifactId + "-configmap-" + envName + ".yml";
		String encryptedProperty = projectArtifactId + "-encrypted-" + envName + ".properties";
		String configPath = path.endsWith("/") ? path + configYaml : path + "//" + configYaml;
		String filePath = path.endsWith("/") ? path + encryptedProperty : path + "//" + encryptedProperty;

		Properties properties = new Properties();
		try (InputStream inputStream = new FileInputStream(filePath)) {
			properties.load(inputStream);
			Map<Object, Object> props = properties.entrySet().stream()
					.filter(map -> (!map.getValue().toString().startsWith("ENC("))).collect(Collectors.toMap(
							map -> map.getKey(), map -> "ENC(" + encrypt(password, map.getValue().toString()) + ")"));
			if (!props.isEmpty()) {
				updateProperties(filePath, props);
				updateYamlFile(configPath, props);
			}
		}
	}

	private static void updateYamlFile(String configPath, Map<Object, Object> props) {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Yaml yaml = new Yaml(options);
		FileWriter os = null;
		try (InputStream is = new FileInputStream(new File(configPath))) {
			Map<String, Object> obj = yaml.load(is);
			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) obj.get("data");
			for (Entry<Object, Object> entry : props.entrySet()) {
				data.put(entry.getKey().toString(), entry.getValue().toString());
			}
			os = new FileWriter(configPath);
			yaml.dump(obj, os);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		}
	}

	private static void updateProperties(String filePath, Map<Object, Object> props) {
		File file = new File(filePath);
		PropertiesConfiguration config = new PropertiesConfiguration();
		PropertiesConfigurationLayout layout = new PropertiesConfigurationLayout();
		try {
			layout.load(config, new InputStreamReader(new FileInputStream(file)));
			for (Entry<Object, Object> entry : props.entrySet()) {
				config.setProperty(entry.getKey().toString(), entry.getValue());
			}
			layout.save(config, new FileWriter(filePath, false));
		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String encrypt(String password, String value) {
		BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
		textEncryptor.setPassword(password);
		return textEncryptor.encrypt(value);
	}

}
