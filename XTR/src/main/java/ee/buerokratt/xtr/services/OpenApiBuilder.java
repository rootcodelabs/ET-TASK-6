package ee.buerokratt.xtr.services;
;
import ee.buerokratt.xtr.domain.XRoadTemplate;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Service;

import java.util.Map;

public class OpenApiBuilder {

    private OpenAPI openAPI;

    public OpenApiBuilder(String name, String version) {
        openAPI = new OpenAPI();
        openAPI.info(
                new Info()
                        .title(name)
                        .version(version)
        );
        openAPI.setPaths(new Paths());
    }

    public OpenApiBuilder addService(XRoadTemplate dsl, String path) {

        PathItem pathItem = new PathItem();
        if (dsl.getMethod().equalsIgnoreCase("POST")) {
            Schema requestBodySchema = new Schema();
            requestBodySchema.setType("object");
            dsl.getParams().forEach(
                    field -> requestBodySchema.addProperties(field,
                            new Schema().type(field.getClass().getSimpleName()))
            );
            RequestBody requestBody = new RequestBody();
            requestBody.setContent(
                    new Content()
                            .addMediaType("application/json",
                                    new MediaType().schema(requestBodySchema))
            );
            ApiResponse success = new ApiResponse();

            pathItem.post( new Operation().requestBody(requestBody)
                    .responses(new ApiResponses().addApiResponse("200", success)));
        } else if (dsl.getMethod().toUpperCase().equals("GET")) {

            dsl.getParams().forEach(
                    field -> {
                        Parameter requestParam = new Parameter();
                        requestParam.setName(field);
                        pathItem.get(new Operation().addParametersItem(requestParam));
                    }
            );
        }

        Paths paths = this.openAPI.getPaths();
        paths.addPathItem("/" + path, pathItem);

        this.openAPI.setPaths(paths);

        return this;
    }

    public OpenAPI build() {
        return this.openAPI;
    }

}