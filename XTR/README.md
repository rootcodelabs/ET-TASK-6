# XTR v3 (alpha)
## REST request/response mappings for XRoad SOAP requests

### Installing  XTR service

#### Building
- Requirements to build standalone: Java17

  Execute:
  ```bash             
  ./gradlew -Pprod clean bootJar
  ``` 
  ```bash
  java -jar build/libs/xtr-0.0.1-SNAPSHOT.jar
  ```

#### Running
- Executing in Docker container:
    
  Execute:
 ```bash             
    docker compose up 
  ```
to see output on current command line; or
 ```bash             
    docker compose up -d
  ```

to run docker container detached.

### Configuration

Configuration values are stored in `application.yml`
with default location in the source tree `src/main/resources/application.yml`.

1. `application.dslPath`: location of XTR DSL's
  ```yaml
  # default value for Docker container
  application:
    dslPath: /DSL
  ```

### Services

Services are defined as YAML-formatted DSLs in folder specified in `application.dslPath` property.

The directory format should be `<dslPath>/<service provider>/<service name>`.

```yaml
params:
  - <list of allowed parameters>
service: <uri of service>
method: <GET|POST>

envelope: <XRoad envelope as XML>
```
* `envelope` can contain handlebars mappings for parameters
* Only parameters specified in `params` will be applied for handlebars mappings

All services are served as POST endpoints.

    POST /<service provider>/<service_name>

with optional parameters as JSON object in request body.

#### Example services

There are few open Äriregister (E-Business Register) services
included as an example. To test if XTR is running and
can access those services, call to specified
endpoints can be made. 

```bash
curl localhost:9020/ar/ettevottegaSeotudIsikud_v1 -H 'Content-type: application/json' -d '{"reg_code": 70006317 }' 
``` 
    