package ee.buerokratt.xtr.controllers;

import ee.buerokratt.xtr.domain.ApiResponse;
import ee.buerokratt.xtr.domain.XRoadTemplate;
import ee.buerokratt.xtr.services.RequestExecutorService;
import ee.buerokratt.xtr.services.XRoadTemplatesService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping(path = "**", consumes = {MediaType.APPLICATION_JSON_VALUE})
public class XRoadRequestController {
    @Autowired
    XRoadTemplatesService serviceReader;

    @Autowired
    RequestExecutorService executor;

    @PostMapping
    public ResponseEntity<ApiResponse<Object>> requestXRoad(@RequestBody(required = false) Map<String, String> requestBody,
                                               @RequestHeader(required = false) Map<String, String> requestHeaders,
                                               HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        String[] uriParts = request.getRequestURI().split("/");

        // Extract service name from URI (e.g., /arireg/ettevottegaSeotudIsikud_v1 -> ettevottegaSeotudIsikud_v1)
        String serviceName = uriParts.length > 2 ? uriParts[2] : "unknown";
        
        log.info("Processing request [traceId: {}] for service: /{}/{}", traceId, uriParts[1], serviceName);

        try {
            XRoadTemplate service = serviceReader.getService(uriParts[1], uriParts[2]);

            log.debug("Loaded template for service: {}", service.getClass().getSimpleName());

            Object result = executor.execute(service, requestBody, serviceName);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.success(result, traceId));
        } catch (Exception e) {
            log.error("Request failed [traceId: {}]", traceId, e);
            throw new RuntimeException("Failed to execute X-Road request: " + e.getMessage(), e);
        }
    }
}
