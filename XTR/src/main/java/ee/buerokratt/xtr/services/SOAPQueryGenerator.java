/**
 * WORK IN PROGRESS. Fill be finalized ASAP.
 */


package ee.buerokratt.xtr.services;

import javax.wsdl.*;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;

import ee.buerokratt.xtr.domain.XRoadTemplate;
import jakarta.servlet.http.Part;
import jakarta.xml.soap.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.wsdl.Service;

import static org.apache.logging.log4j.message.ParameterizedMessage.deepToString;

@Slf4j
@org.springframework.stereotype.Service
public class SOAPQueryGenerator {

    public SOAPQueryGenerator() {
/*        try {
            generateSoapEnvelopes("/app/wsdl/lkf_9.wsdl");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }*/
    }

    public void generateSoapEnvelopes(String wsdlPath) throws Exception {

        WSDLFactory factory = WSDLFactory.newInstance();
        WSDLReader reader = factory.newWSDLReader();
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Definition definition = reader.readWSDL(wsdlPath);

        // Iterate over the services defined in the WSDL
        for (Object serviceObj : definition.getServices().values()) {
            Service service = (Service) serviceObj;
            QName serviceName = service.getQName();
            log.debug("Service: " + serviceName);

            log.debug(deepToString(definition.getMessages()));

            // Iterate over the ports in the service
            for (Object portObj : service.getPorts().values()) {
                Port port = (Port) portObj;
                QName portName = QName.valueOf(port.getName());
                log.debug("Port: " + portName);

                // Generate SOAP envelope for each operation
                Binding binding = port.getBinding();
                List<BindingOperation> bindingOperations = binding.getBindingOperations();
                for (BindingOperation bindingOperation : bindingOperations) {
                    String operationName = bindingOperation.getName();
                    log.debug("Generating SOAP Envelope for Operation: " + operationName);
                    log.debug("Using namespace: "+ serviceName.getNamespaceURI());
                    XRoadTemplate envelope = generateSoapEnvelope(serviceName.getNamespaceURI(), operationName, bindingOperation, definition);
                    log.debug("Generated template: \n"+ envelope.toYaml());
                }
            }
        }
    }

    private XRoadTemplate generateSoapEnvelope(String namespaceUri, String operationName, BindingOperation bindingOperation, Definition definition) throws SOAPException, IOException {

        XRoadTemplate template = new XRoadTemplate();
        template.setService( namespaceUri   );
        template.setMethod("POST");
        template.setParams(new ArrayList<String>() {});

        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();

        SOAPPart soapPart = soapMessage.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        SOAPBody body = envelope.getBody();

        QName bodyName = new QName(namespaceUri, operationName, "lkf");
        SOAPBodyElement bodyElement = body.addBodyElement(bodyName);

        QName iName = QName.valueOf("requestHeader");
        Message inputMessage = definition.getMessage(iName);

        log.debug("qname: " + iName.toString());
        log.debug("Inputmessage: "+ inputMessage);

        if (inputMessage != null) {
            for (Object partObj : inputMessage.getParts().values()) {
                Part part = (Part) partObj;
                QName partName = new QName(part.getName());
                String paramName = part.getName();

                template.getParams().add(paramName);

                bodyElement.addChildElement(partName)
                        .addTextNode("<%s>{{ %s }}</%s>".formatted(paramName, paramName, paramName));
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            soapMessage.writeTo(outputStream);
            template.setEnvelope(outputStream.toString()); // Convert to string and return
        }

        return template;
    }

}