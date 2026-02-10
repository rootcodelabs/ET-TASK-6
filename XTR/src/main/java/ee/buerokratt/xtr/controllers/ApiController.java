package ee.buerokratt.xtr.controllers;

import ee.buerokratt.xtr.services.XRoadTemplatesService;
import io.swagger.v3.core.util.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class ApiController {

    @Autowired
    XRoadTemplatesService serviceReader;

    @GetMapping("/api")
    public ResponseEntity<Object> openApiSpec(){
        return status(200).contentType(MediaType.APPLICATION_JSON)
                .body(Json.pretty(serviceReader.getApi()    ));
    }


}
