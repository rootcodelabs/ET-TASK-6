package ee.buerokratt.xtr.services;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class HandlebarsHelper {

    @Value("${application.xroad-instance}")
    String xroadInstance;

    @Value("${application.client-data.member-class}")
    String memberClass;

    @Value("${application.client-data.member-code}")
    String memberCode;

    @Value("${application.client-data.subsystem-code}")
    String subsystemCode;

    public String apply(String template, Map<String, String> values) throws IOException {
        Handlebars hbs = new Handlebars();
        Template result = hbs.compileInline(template);

        Map<String, String> localValues = new HashMap<>();
        localValues.put("generate_uuid", generateUUID());
        localValues.put("generate_client", generateClientEnvelope());
        localValues.put("generate_instance", xroadInstance);

        // Merge maps to ensure all variables are available
        Map<String, Object> combinedValues = new HashMap<>();
        combinedValues.putAll(localValues);
        if (values != null) {
            combinedValues.putAll(values);
        }

        return result.apply(combinedValues);
    }

    private String generateClientEnvelope() {
        return """
                <xroad:client id:objectType="SUBSYSTEM">
                <id:xRoadInstance>%s</id:xRoadInstance>
                <id:memberClass>%s</id:memberClass>
                <id:memberCode>%s</id:memberCode>
                <id:subsystemCode>%s</id:subsystemCode>
                </xroad:client>"""
                .formatted(xroadInstance,
                        memberClass,
                        memberCode,
                        subsystemCode);
    }

    private String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
