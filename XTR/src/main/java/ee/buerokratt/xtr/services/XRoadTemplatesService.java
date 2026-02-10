package ee.buerokratt.xtr.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.buerokratt.xtr.domain.XRoadTemplate;
import ee.buerokratt.xtr.domain.YamlXRoadTemplate;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class XRoadTemplatesService {

    public static String RESQL_SERVICE_URI = "";

    final ObjectMapper mapper;

    final OpenApiBuilder openApiBuilder;

    @Getter
    OpenAPI api;

    String configPath;

    Map<String, Map<String, XRoadTemplate>> services;

    @Autowired
    public XRoadTemplatesService(@Qualifier("ymlMapper") ObjectMapper mapper,
                                 @Value("${application.dslPath}") String configPath) {
        this.mapper = mapper;
        this.configPath = configPath;
        this.services = new HashMap<>();
        this.openApiBuilder = new OpenApiBuilder("XTR", "3.0-beta");
        readServicesFromFS();
    }

    public void readServicesFromFS() {
        readServicesFromFS(configPath);
//        readServiesFromDB();

        this.api = openApiBuilder.build();

        log.info("Built OpenAPI spec: " + Yaml.pretty(this.api));
    }

    public void readServiesFromDB() {
        RestClient client = RestClient.create();

        log.info("Requesting list of wsdl's from Resql");

        List<String> wsdlUris = client.post()
                .uri(RESQL_SERVICE_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {});

        for (String uri: wsdlUris) {
            try {
                readServicesFromUri(uri);
            } catch (IOException ioex) {
                log.error("Failed to read WSDL from %s: %s"
                        .formatted(uri, ioex.getCause()), ioex);
            }
        };
    }

    public void readServicesFromFS(String path) {
        try (Stream<Path> paths = Files.walk(Paths.get(path))) {
            paths.filter(f -> !f.toFile().isDirectory())
                    .filter(f -> !f.toString().contains("Ruuter.public"))
                    .forEach(this::readServicesFromFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void readServicesFromFile(Path filePath) {
        File file = filePath.toFile();
        log.info("Loading XRoad requests from " + file.getPath());
        YamlXRoadTemplate service = readService(file);
        log.info("Loaded service: " + service.toString());

        String[] pathParts = file.getPath().split("/");
        String groupName = pathParts[pathParts.length-2];
        String serviceName = pathParts[pathParts.length-1].substring(0, pathParts[pathParts.length-1].indexOf(".y"));
        addService(groupName, serviceName, service);
        openApiBuilder.addService(service, "%s/%s".formatted(groupName, serviceName));
    }

    void readServicesFromUri(String uri) throws IOException {
        // Disabled for security reasons (SSRF prevention)
        throw new SecurityException("Loading services from arbitrary URIs is disabled.");
        
        /* 
        URL url = new URL(uri);

        try (InputStream inputStream = url.openStream();
        ...
        */
    }

    private YamlXRoadTemplate readService(File serviceFile) {
        try {
            YamlXRoadTemplate map = mapper.readValue(serviceFile, YamlXRoadTemplate.class);
            return map;
        } catch (IOException e) {
            log.error("Could not create service", e);
            throw new RuntimeException(e);
        }
    }

    public void addService(String groupName, String serviceName, XRoadTemplate template) {
        if (!this.services.containsKey(groupName))
            this.services.put(groupName, new HashMap<>());
        log.info("Adding "+ serviceName + " to service " + groupName);
        this.services.get(groupName).put(serviceName, template);
    }

    public XRoadTemplate getService(String group, String service) {
        return services.get(group).get(service);
    }

    void dumpServices(String configPath, boolean overwrite) throws IOException {
        for (Map.Entry<String, Map<String, XRoadTemplate>> e : this.services.entrySet()) {
            for (Map.Entry<String, XRoadTemplate> entry : e.getValue().entrySet()) {
                dumpService(configPath, overwrite, e.getKey(), entry.getKey(), entry.getValue());
            }
        }
    }

    void dumpService(String configPath, boolean overwrite,
                     String namespace, String name, XRoadTemplate dsl) throws IOException {
        String filepath = configPath + "/ " + namespace + "/" + name;
        Path path = Paths.get(filepath);
        if (! Files.exists(path) && !overwrite) {
            Files.write(path, dsl.toString().getBytes(), StandardOpenOption.CREATE);
        } else {
            log.warn("File %s already exists and overwrite is turned off".formatted(filepath ));
        }
    }

    void generateOpenAPI() {

    }

}
