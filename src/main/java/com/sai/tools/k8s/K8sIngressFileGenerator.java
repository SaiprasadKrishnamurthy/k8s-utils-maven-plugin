package com.sai.tools.k8s;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 *  Generates Ingress Files from the deployment yaml
 *  
 * 
 * @author Kannan.Ramasubbu
 *
 */
public class K8sIngressFileGenerator {

	private static Map<String, List<String>> serviceName = new HashMap<String, List<String>>();
	private static final String DEPLOYMENT_FILES_BASE_DIR = "target/deployment";
	
	/* retain the space intentation */
    private static String pattern = "        - path: /${serviceName} \n"
    				+"          backend: \n"
    				+"           serviceName: ${serviceName} \n"
    				+"           servicePort: ${port} \n";
    				
    
	public static void main(String[] args) throws IOException {

		Files.walk(Paths.get(
				DEPLOYMENT_FILES_BASE_DIR))
				.filter(path -> path.toFile().isFile()).filter(path -> path.toFile().getName().endsWith(".yml"))
				.filter(path -> path.toFile().getName().contains("deployment"))
				.forEach(path -> extractServiceNameAndPortNumber(path));
		 generateYAMLContent();
		

	}

	/**
	 *  It loos through the serviceName maps and creates Ingress path
	 *  The generated content is updated in the template
	 */
	private static void generateYAMLContent() {
		 try {
			String configMapTemplate = IOUtils.toString(new FileInputStream("SOURCES/ingress/ingress-template.yml"), Charset.defaultCharset());
			serviceName.keySet()
					   .stream()
					   .filter(key -> key.length() > 0)
					   .forEach(env ->  {
						   StringBuffer mapings = new StringBuffer();
						   serviceName.get(env).forEach(test -> {
							   String serviceName = test.substring(0, test.indexOf("#"));
							   String portNumber = test.substring(test.indexOf("#")+1);
							   mapings.append(pattern.replace("${serviceName}", serviceName).replace("${port}", portNumber));
							   mapings.append("\n");
						   });
						   try {
							FileUtils.write(Paths.get("target", "deployment", "Ingress-"+ env + ".yml").toFile(), configMapTemplate.replace("${paths}", mapings.toString()), Charset.defaultCharset());
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					   });
		} catch (Exception e) {

			e.printStackTrace();
		} 
		
		
	}

	/**
	 *  Retrieves Artifact Name, Env Name and Port from the file and adds in the hashmap.
	 *  Hashmap contains
	 *  
	 *  <env name> - List <Sring> (artifact_name#portnumber)
	 * 
	 * @param path
	 */

	private static void extractServiceNameAndPortNumber(Path path) {
		File file = path.toFile();
		String fileName = file.getName();
		// audit-stream-processor-deployment-default.yml
		String artifactName = fileName.substring(0, fileName.indexOf("-de"));
		String envName = fileName.substring(fileName.lastIndexOf("-") + 1, fileName.indexOf("."));
		String port = "";
		try {
			DumperOptions options = new DumperOptions();
			options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
			options.setPrettyFlow(true);
			Yaml yaml = new Yaml(options);
			Iterable<Object> obj = yaml.loadAll(new FileInputStream(file));
			Iterator<Object> iterator = obj.iterator();
			while (iterator.hasNext()) {
				Map<String, Object> configMap = (Map<String, Object>) iterator.next();
				if ("Service".equals(configMap.get("kind"))) {
					Object portExtracted = ((Map<String, Object>) ((List<Object>) ((Map<String, Object>) configMap
							.get("spec")).get("ports")).get(0)).get("port");
					port = String.valueOf(portExtracted);
				}

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		if (!serviceName.containsKey(envName)) {
			serviceName.put(envName, new ArrayList<String>());

		}

		serviceName.get(envName).add(artifactName + "#" + port);

	}

}
