package ee.buerokratt.xtr.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import ee.buerokratt.xtr.domain.XRoadTemplate;
import ee.buerokratt.xtr.filters.ResponseFilterService;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.ClientAuth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.stream.XMLInputFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class RequestExecutorService {

    @Autowired
    HandlebarsHelper handlebarsHelper;

    @Autowired
    ResponseFilterService responseFilterService;

    Properties properties;

    @Value("${application.security-server}")
    private String securityServer;

    @Value("${application.ssl.keystore-password}")
    private String keystorePassword;

    @Value("${application.ssl.keystore}")
    private String keystorePath;

    // Optional: only needed when security-server uses HTTPS (for mutual TLS)
    // @Value("${server.ssl.trust-store}")
    @Value("${server.ssl.trust-store:#{null}}")
    private String trustStorePath;

    public Object execute(XRoadTemplate template, Map<String, String> params, String serviceName) throws Exception {
        String payload = template.getPayload(params, handlebarsHelper);
        String xmlResponse;
        
        if (template.getService() == null || template.getService().isBlank())
            xmlResponse = doRequestTowarsdSS(template.getMethod(), payload);
        else
            xmlResponse = doRequest(template.getService(), template.getMethod(), payload, template.getHeaders());
        
        // Convert XML to JSON
        JsonNode jsonResponse = xmlToJsonNode(xmlResponse);
        
        // Filter response based on field configuration
        JsonNode filteredResponse = responseFilterService.filterResponse(serviceName, jsonResponse);
        
        // Convert JsonNode to Map
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.convertValue(filteredResponse, Map.class);
    }


    private String doRequest(String serviceURI, String method, String payload, Map<String, String> headers) {
        RestClient client = RestClient.builder().build();

        log.info("Sending {} request to endpoint {} (payload size: {} bytes)", method, serviceURI, payload.length());

        var spec = client.method(HttpMethod.valueOf(method))
                .uri(serviceURI);

        boolean contentTypeSet = false;
        if (headers != null && !headers.isEmpty()) {
            log.info("Adding headers: " + headers);
            spec.headers(httpHeaders -> headers.forEach(httpHeaders::add));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                    try {
                        spec.contentType(MediaType.parseMediaType(entry.getValue()));
                        contentTypeSet = true;
                    } catch (Exception e) {
                        log.warn("Invalid Content-Type header value: " + entry.getValue(), e);
                    }
                }
            }
        }

        if (!contentTypeSet) {
            spec.contentType(MediaType.TEXT_XML);
        }

        return spec.body(payload)
                .retrieve()
                .toEntity(String.class)
                .getBody();
    }


    private String doRequestTowarsdSS(String method, String payload) throws Exception {
        WebClient client;
        if (securityServer.toLowerCase().startsWith("https")) {
            client = createWebClientWithSsl();
        } else {
            client = WebClient.builder().build();
        }

        log.info("Sending {} request to Security Server {} (payload size: {} bytes)", method, securityServer, payload.length());

        return client
                .method(HttpMethod.valueOf(method))
                .uri(securityServer)
                .contentType(MediaType.TEXT_XML)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .toEntity(String.class)
                .block()
                .getBody();
    }

    private WebClient createWebClientWithSsl() throws Exception {
        // 1. Load the Keystore (Client Identity)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream keyStoreStream = new FileInputStream(keystorePath)) {
            keyStore.load(keyStoreStream, keystorePassword.toCharArray());
        }

        // 2. Load the Truststore (Trusted Servers)
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        // Remove "file:" prefix if present
        String cleanTrustPath = trustStorePath.replace("file:", "");
        try (FileInputStream trustStoreStream = new FileInputStream(cleanTrustPath)) {
            trustStore.load(trustStoreStream, keystorePassword.toCharArray());
        }

        // 3. Init Trust Manager
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore); 

        // 4. Init Key Manager (Not used directly, we extract key manually)
        // KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        // kmf.init(keyStore, keystorePassword.toCharArray());

        // 5. Create Netty SSL Context with Explicit Key (Forces sending the cert)
        // We look for the 'xtr-server' alias we created
        String alias = "xtr-server";
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keystorePassword.toCharArray());
        java.security.cert.Certificate[] certChain = keyStore.getCertificateChain(alias);
        
        if (privateKey == null || certChain == null) {
            throw new RuntimeException("Could not find key/cert chain for alias '" + alias + "' in keystore");
        }
        
        // Convert to X509Certificate array
        X509Certificate[] x509CertChain = Arrays.copyOf(certChain, certChain.length, X509Certificate[].class);

        log.info("Loaded client certificate for alias: " + alias + " with chain length: " + x509CertChain.length);

        io.netty.handler.ssl.SslContext nettySslContext = SslContextBuilder.forClient()
                .keyManager(privateKey, x509CertChain)
                .trustManager(tmf)
                .build();

        // 6. Bind to HttpClient
        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(nettySslContext));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private JsonNode xmlToJsonNode(String xmlPayload) throws JsonProcessingException {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XmlMapper xmlMapper = new XmlMapper(new XmlFactory(inputFactory));
        JsonNode node = xmlMapper.readTree(xmlPayload);
        
        // Return the Body node as JsonNode for filtering
        return node.get("Body");
    }

}
