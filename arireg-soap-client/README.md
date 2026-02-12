# Arireg SOAP Client

A SOAP proxy client for communicating with the Estonian Business Registry (Äriregister) X-Road service.

## Overview

This service acts as a SOAP client/proxy that forwards requests to the Estonian Business Registry endpoint at `https://ariregxmlv6.rik.ee/`. It provides a local endpoint for testing and development purposes.

## Features

- SOAP request forwarding to Äriregister X-Road service
- **Service name transformation**: Converts between camelCase (client) and underscore (backend) formats
  - Client-facing: `ettevottegaSeotudIsikudV1`
  - Backend: `ettevottegaSeotudIsikud_v1`
- WSDL endpoint for service discovery
- X-Road header propagation
- Request/response logging with operation identification
- Health check endpoint
- Docker support for easy deployment
- TLS/SSL support with configurable certificate validation
- Comprehensive error handling with specific error messages
- Connection pooling and timeout management

## Quick Start

### Run Locally

1. **Build the application:**
   ```bash
   go build -o arireg-soap-client.exe main.go
   ```

2. **Run the server:**
   ```bash
   ./arireg-soap-client.exe
   ```

   The server will start on port `1236`:
   - SOAP endpoint: `http://localhost:1236/soap`
   - Health check: `http://localhost:1236/health`

### Run with Docker

1. **Build and run with Docker Compose:**
   ```bash
   docker-compose up -d --build
   ```

2. **Check logs:**
   ```bash
   docker-compose logs -f
   ```

3. **Stop the service:**
   ```bash
   docker-compose down
   ```

## Example Usage

### Query Business Registry

Send a SOAP request to get persons associated with a business:

```bash
curl -X POST 'http://localhost:1236/soap' \
  --header 'Content-Type: text/xml' \
  --data '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:prod="http://arireg.x-road.eu/producer/">
   <soapenv:Header/>
   <soapenv:Body>
      <prod:ettevottegaSeotudIsikudV1>
         <prod:keha>
            <prod:ariregistri_kood>70006317</prod:ariregistri_kood>
         </prod:keha>
      </prod:ettevottegaSeotudIsikudV1>
   </soapenv:Body>
</soapenv:Envelope>'
```

**Note**: You can use either camelCase (`ettevottegaSeotudIsikudV1`) or underscore (`ettevottegaSeotudIsikud_v1`) format - the proxy automatically transforms it to the correct backend format.

### Get WSDL

```bash
curl http://localhost:1236/soap?wsdl
# or
curl http://localhost:1236/wsdl
```

### Health Check

```bash
curl http://localhost:1236/health
```
