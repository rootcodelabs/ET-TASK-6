package ee.buerokratt.xtr.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ee.buerokratt.xtr.services.HandlebarsHelper;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class XRoadTemplate {
    private List<String> params;

    private String service;
    private String method;
    private String envelope;
    private Map<String, String> headers;

    protected Map<String, String> filterParams(XRoadTemplate template, Map<String, String> params) {
        Map<String, String> returnParams = new HashMap<>();
        params.entrySet().forEach(entry -> {
            if (template.getParams().contains(entry.getKey()))
                returnParams.put(entry.getKey(), entry.getValue());
        });
        return returnParams;
    }

    public String getPayload(Map<String, String> params, HandlebarsHelper helper) throws Exception{
        return helper.apply(getEnvelope(), filterParams(this, params));
    }

    public String toYaml() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
    