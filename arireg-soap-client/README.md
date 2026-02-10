# Arireg SOAP Client

A SOAP proxy client for communicating with the Estonian Business Registry (Äriregister) X-Road service.

## Overview

This service acts as a SOAP client/proxy that forwards requests to the Estonian Business Registry endpoint at `https://ariregxmlv6.rik.ee/`. It provides a local endpoint for testing and development purposes.

## Features

- SOAP request forwarding to Äriregister X-Road service
- Request/response logging
- Health check endpoint
- Docker support for easy deployment
- TLS/SSL support for secure communication

## Endpoints

- **POST /soap** - SOAP proxy endpoint (forwards to Äriregister)
- **GET /** - Root endpoint with service information
- **GET /health** - Health check endpoint

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
      <prod:ettevottegaSeotudIsikud_v1>
         <prod:keha>
            <prod:ariregistri_kood>70006317</prod:ariregistri_kood>
         </prod:keha>
      </prod:ettevottegaSeotudIsikud_v1>
   </soapenv:Body>
</soapenv:Envelope>'
```

### Health Check

```bash
curl http://localhost:1236/health
```

Response:
```json
{
  "status": "healthy",
  "target": "https://ariregxmlv6.rik.ee/"
}
```

## Configuration

The following constants can be modified in `main.go`:

- `ARIREG_ENDPOINT` - Target Äriregister endpoint (default: `https://ariregxmlv6.rik.ee/`)
- `SERVER_PORT` - Local server port (default: `:1236`)

## Logging

The service provides detailed logging including:
- Incoming request information
- Request size in bytes
- Operation identification
- Response status and size
- Request duration

Example log output:
```
INFO    Starting Arireg SOAP Client on :1236
INFO    SOAP endpoint: http://localhost:1236/soap
INFO    Target endpoint: https://ariregxmlv6.rik.ee/
INFO    127.0.0.1:54321 - HTTP/1.1 POST /soap
INFO    Received SOAP request (512 bytes)
INFO    Operation: ettevottegaSeotudIsikud_v1 for code: 70006317
INFO    Forwarding request to https://ariregxmlv6.rik.ee/
INFO    Received response from https://ariregxmlv6.rik.ee/: HTTP 200 (2048 bytes) in 1.2s
INFO    Completed request in 1.2s
```

## Architecture

```
Client Request → [arireg-soap-client:1236] → HTTPS → [ariregxmlv6.rik.ee]
                                                  ↓
Client Response ← [arireg-soap-client:1236] ← Response
```

The client:
1. Receives SOAP requests on port 1236
2. Validates the SOAP envelope structure
3. Forwards the request to the Estonian Business Registry
4. Returns the response to the original caller
5. Logs all activity for monitoring

## Security Considerations

- The service uses HTTPS when communicating with the Äriregister endpoint
- TLS certificate validation is enabled by default
- For development/testing only, you can disable certificate validation by setting `InsecureSkipVerify: true` in the transport configuration

## Requirements

- Go 1.21 or higher
- Network access to `https://ariregxmlv6.rik.ee/`
- Docker (optional, for containerized deployment)

## Troubleshooting

### Connection Errors

If you see connection errors to the Äriregister endpoint:
- Check your network connectivity
- Verify firewall settings allow HTTPS outbound connections
- Ensure the endpoint URL is correct

### TLS/SSL Errors

If you encounter certificate validation errors:
- Ensure your system's CA certificates are up to date
- For testing only, you can temporarily disable validation (not recommended for production)

## License

See LICENSE file for details.
