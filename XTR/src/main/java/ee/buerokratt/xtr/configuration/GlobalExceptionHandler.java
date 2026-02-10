package ee.buerokratt.xtr.configuration;

import ee.buerokratt.xtr.domain.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.util.UUID;

/**
 * Global exception handler for the XTR application.
 * Catches exceptions and formats them into standardized ApiResponse objects.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle SSL/TLS errors
     */
    @ExceptionHandler(SSLException.class)
    public ResponseEntity<ApiResponse<Object>> handleSSLException(SSLException ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("SSL/TLS error occurred [traceId: {}]: {}", traceId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiResponse.error("SSL_ERROR", 
                        "Secure connection to X-Road Security Server failed", 
                        traceId)
        );
    }

    /**
     * Handle connection errors
     */
    @ExceptionHandler(ConnectException.class)
    public ResponseEntity<ApiResponse<Object>> handleConnectException(ConnectException ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Connection error occurred [traceId: {}]: {}", traceId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiResponse.error("CONNECTION_ERROR", 
                        "Could not connect to X-Road Security Server", 
                        traceId)
        );
    }

    /**
     * Handle WebClient (HTTP) errors
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Object>> handleWebClientException(WebClientResponseException ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("X-Road request failed [traceId: {}]: {} {}", traceId, ex.getStatusCode(), ex.getMessage());
        
        ApiResponse.ErrorDetails errorDetails = ApiResponse.ErrorDetails.builder()
                .code("XROAD_ERROR")
                .message("X-Road Security Server returned an error")
                .details(ex.getStatusText())
                .build();
        
        return ResponseEntity.status(ex.getStatusCode()).body(
                ApiResponse.error(errorDetails)
        );
    }

    /**
     * Handle illegal argument exceptions (validation errors)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Invalid request [traceId: {}]: {}", traceId, ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.error("INVALID_REQUEST", 
                        ex.getMessage(), 
                        traceId)
        );
    }

    /**
     * Handle runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Runtime error occurred [traceId: {}]", traceId, ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error("INTERNAL_ERROR", 
                        "An unexpected error occurred. Please contact support with trace ID: " + traceId, 
                        traceId)
        );
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unexpected error occurred [traceId: {}]", traceId, ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.error("INTERNAL_ERROR", 
                        "An unexpected error occurred. Please contact support with trace ID: " + traceId, 
                        traceId)
        );
    }
}
