package ee.buerokratt.xtr.filters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Service
public class ResponseFilterService {
    
    private static final Logger log = LoggerFactory.getLogger(ResponseFilterService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String adminApiUrl;
    
    public ResponseFilterService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${application.admin-api-url}") String adminApiUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.adminApiUrl = adminApiUrl;
    }
    
    /**
     * Filter response based on field configuration from admin database.
     * Only includes selected fields and masks sensitive data.
     */
    public JsonNode filterResponse(String serviceName, JsonNode response) {
        try {
            // Get field configuration from admin API
            FilterConfig config = getFilterConfig(serviceName);
            
            if (config == null || config.selectedFields.isEmpty()) {
                log.debug("No field configuration found for service: {}, returning full response", serviceName);
                return response;
            }
            
            log.info("Filtering response for service: {} (selected fields: {}, sensitive fields: {})", 
                    serviceName, config.selectedFields.size(), config.sensitiveFields.size());
            
            // Filter to include only selected fields and mask sensitive ones
            JsonNode filtered = filterFields(response, config.selectedFields, config.sensitiveFields);
            
            return filtered;
            
        } catch (Exception e) {
            log.error("Error filtering response for service {}: {}", serviceName, e.getMessage());
            // On error, return original response
            return response;
        }
    }
    
    private FilterConfig getFilterConfig(String serviceName) {
        try {
            String url = adminApiUrl + "/api/config/service/" + serviceName + "/filter";
            log.debug("Fetching filter config from: {}", url);
            
            FilterConfigResponse response = restTemplate.getForObject(url, FilterConfigResponse.class);
            
            if (response != null && response.selectedFields != null && response.sensitiveFields != null) {
                FilterConfig config = new FilterConfig();
                config.selectedFields = new HashSet<>(response.selectedFields);
                config.sensitiveFields = new HashSet<>(response.sensitiveFields);
                log.info("Loaded filter config for {}: {} selected fields, {} sensitive fields", 
                        serviceName, config.selectedFields.size(), config.sensitiveFields.size());
                log.debug("Selected fields: {}", config.selectedFields);
                log.debug("Sensitive fields: {}", config.sensitiveFields);
                return config;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch filter config for service '{}': {}", serviceName, e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Recursively filter fields based on configuration.
     * Only includes selected fields and masks sensitive ones.
     * Preserves structural nodes (objects/arrays) while filtering leaf fields.
     */
    private JsonNode filterFields(JsonNode node, Set<String> selectedFields, Set<String> sensitiveFields) {
        if (node == null) {
            return null;
        }
        
        if (node.isObject()) {
            ObjectNode filtered = objectMapper.createObjectNode();
            Iterator<String> fieldNames = node.fieldNames();
            
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldValue = node.get(fieldName);
                
                // For objects and arrays, always recurse to filter their children
                if (fieldValue.isObject() || fieldValue.isArray()) {
                    JsonNode filteredChild = filterFields(fieldValue, selectedFields, sensitiveFields);
                    // Only include structural nodes if they have content after filtering
                    if (filteredChild != null && 
                        ((filteredChild.isObject() && filteredChild.size() > 0) ||
                         (filteredChild.isArray() && filteredChild.size() > 0) ||
                         (!filteredChild.isObject() && !filteredChild.isArray()))) {
                        filtered.set(fieldName, filteredChild);
                    }
                } else {
                    // For leaf nodes (primitives), check if field should be included
                    if (shouldIncludeField(fieldName, selectedFields)) {
                        // Mask sensitive fields
                        if (isSensitiveField(fieldName, sensitiveFields)) {
                            filtered.put(fieldName, "[SENSITIVE_DATA_MASKED]");
                        } else {
                            filtered.set(fieldName, fieldValue);
                        }
                    }
                }
            }
            return filtered;
        } else if (node.isArray()) {
            ArrayNode filteredArray = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                JsonNode filteredItem = filterFields(item, selectedFields, sensitiveFields);
                // Only add items that have content after filtering
                if (filteredItem != null && 
                    ((filteredItem.isObject() && filteredItem.size() > 0) ||
                     (filteredItem.isArray() && filteredItem.size() > 0) ||
                     (!filteredItem.isObject() && !filteredItem.isArray()))) {
                    filteredArray.add(filteredItem);
                }
            }
            return filteredArray;
        }
        
        return node;
    }
    
    private boolean shouldIncludeField(String fieldName, Set<String> selectedFields) {
        if (selectedFields.isEmpty()) {
            return true; // No filter, include all
        }
        // Check exact match or prefix match for nested fields (e.g., "keha.isikukood")
        return selectedFields.stream().anyMatch(sf -> 
            sf.equals(fieldName) || sf.endsWith("." + fieldName) || fieldName.equals(sf.substring(sf.lastIndexOf('.') + 1))
        );
    }
    
    private boolean isSensitiveField(String fieldName, Set<String> sensitiveFields) {
        // Check exact match or prefix match for nested fields
        return sensitiveFields.stream().anyMatch(sf -> 
            sf.equals(fieldName) || sf.endsWith("." + fieldName) || fieldName.equals(sf.substring(sf.lastIndexOf('.') + 1))
        );
    }
    
    private static class FilterConfig {
        Set<String> selectedFields = new HashSet<>();
        Set<String> sensitiveFields = new HashSet<>();
    }
    
    private static class FilterConfigResponse {
        public String service;
        
        @JsonProperty("selected_fields")
        public List<String> selectedFields = new ArrayList<>();
        
        @JsonProperty("sensitive_fields")
        public List<String> sensitiveFields = new ArrayList<>();
    }
}
