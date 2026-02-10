/**
 * WORK IN PROGRESS. Fill be finalized ASAP.
 */


package ee.buerokratt.xtr.services;

// import ee.buerokratt.xtr.admin.dto.WsdlFieldSchema; // TODO: Implement when admin DTO is ready
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;


@Slf4j
@Service
public class DynamicWSDLService {

    public void start() {
        String wsdlFilePath = "ar_97.wsdl"; // Update the path to your local WSDL file
        try {
            loadFromFile(wsdlFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadFromFile(String wsdlFilePath) throws Exception {
        String wsdlContent = readWsdl(wsdlFilePath);

        // SAFE: Skipping external schema downloads to prevent SSRF
        // List<String> schemaUrls = extractSchemaUrls(wsdlContent);
        // downloadXsd(schemaUrls);

        // Extract schema namespaces
        Map<String, Element> schemaNamespaces = extractSchemaNamespaces(wsdlContent);
        log.debug("Schema namespaces found: " + schemaNamespaces);

        // Parse WSDL for operations
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document root = builder.parse(new ByteArrayInputStream(wsdlContent.getBytes()));

        Map<String, List<String>> operations = new HashMap<>();

        NodeList operationNodes = root.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "operation");
        for (int i = 0; i < operationNodes.getLength(); i++) {
            Element operation = (Element) operationNodes.item(i);
            String opName = operation.getAttribute("name");
            NodeList inputNodes = operation.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "input");
            if (inputNodes.getLength() > 0) {
                String messageName = inputNodes.item(0).getAttributes().getNamedItem("message").getNodeValue();
                // Extract parameters from the corresponding message
                List<String> parameters = extractParameters(messageName, root);
                operations.put(opName, parameters);
            }
        }

        // Build SOAP envelopes for each operation in the custom format
        for (Map.Entry<String, List<String>> entry : operations.entrySet()) {
            String opName = entry.getKey();
            List<String> params = entry.getValue();
            byte[] soapEnvelopeBytes = buildCustomSoapEnvelope(opName, params);
            String prettySoapEnvelope = toXML(soapEnvelopeBytes);
            log.debug("SOAP Envelope for operation '" + opName + "':\n" + prettySoapEnvelope + "\n");
        }
    }

    public static String readWsdl(String filePath) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)), "UTF-8");
    }

    public static List<String> extractSchemaUrls(String wsdlContent) throws Exception {
        List<String> schemas = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(wsdlContent.getBytes()));
        NodeList schemaNodes = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "schema");

        for (int i = 0; i < schemaNodes.getLength(); i++) {
            Element schema = (Element) schemaNodes.item(i);
            String schemaLocation = schema.getAttribute("schemaLocation");
            if (!schemaLocation.isEmpty()) {
                schemas.add(schemaLocation);
            }
        }
        return schemas;
    }

    public static Map<String, Element> extractSchemaNamespaces(String wsdlContent) throws Exception {
        Map<String, Element> namespaces = new HashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(wsdlContent.getBytes()));
        NodeList schemaNodes = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/schema", "schema");

        for (int i = 0; i < schemaNodes.getLength(); i++) {
            Element schema = (Element) schemaNodes.item(i);
            String targetNamespace = schema.getAttribute("targetNamespace");
            namespaces.put(targetNamespace, schema);
        }
        return namespaces;
    }

    public static void downloadXsd(List<String> schemaUrls) {
        // Disabled for security reasons (SSRF prevention)
        log.warn("Auto-downloading XSDs is disabled for security reasons.");
    }

    public static List<String> extractParameters(String operationName, Document root) {
        List<String> parameters = new ArrayList<>();
        NodeList messageNodes = root.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "message");

        for (int i = 0; i < messageNodes.getLength(); i++) {
            Element message = (Element) messageNodes.item(i);
            if (message.getAttribute("name").equals(operationName + "Request")) {
                NodeList partNodes = message.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "part");
                for (int j = 0; j < partNodes.getLength(); j++) {
                    Element part = (Element) partNodes.item(j);
                    parameters.add(part.getAttribute("name"));
                }
            }
        }
        return parameters;
    }

    public static byte[] buildCustomSoapEnvelope(String operationName, List<String> parameters) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element envelope = doc.createElement("soapenv:Envelope");
        envelope.setAttribute("xmlns:soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
        envelope.setAttribute("xmlns:prod", "http://ariregistri.x-road.eu/producer/");
        doc.appendChild(envelope);

        Element header = doc.createElement("soapenv:Header");
        envelope.appendChild(header);

        Element body = doc.createElement("soapenv:Body");
        envelope.appendChild(body);

        Element operation = doc.createElement("prod:" + operationName);
        body.appendChild(operation);

        Element keha = doc.createElement("prod:keha");
        operation.appendChild(keha);

        // Add all parameters as placeholders in the body
        for (String param : parameters) {
            Element paramElement = doc.createElement("prod:" + param);
            paramElement.setTextContent("{{ " + param + " }}"); // Placeholder for parameters
            keha.appendChild(paramElement);
        }

        // Convert document to bytes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
        return outputStream.toByteArray();
    }

    public static String toXML(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
    
    // TODO: Uncomment when WsdlFieldSchema DTO is implemented
    /*
    public List<WsdlFieldSchema> extractFieldSchema(String wsdlFilePath) throws Exception {
        // Method implementation commented out - requires WsdlFieldSchema DTO
        return new ArrayList<>();
    }
    
    private String extractMessageName(Node messageNode) {
        // Method implementation commented out - requires WsdlFieldSchema DTO
        return "";
    }
    
    private List<WsdlFieldSchema> extractFieldsFromMessage(Document doc, String messageName, 
                                                           String operationName, String category, 
                                                           String direction) {
        // Method implementation commented out - requires WsdlFieldSchema DTO
        return new ArrayList<>();
    }
    
    private List<WsdlFieldSchema> extractFieldsFromSchema(Element schema) {
        // Method implementation commented out - requires WsdlFieldSchema DTO
        return new ArrayList<>();
    }
    */
}
