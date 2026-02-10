package ee.buerokratt.xtr.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard unified response wrapper for all API responses.
 * Provides consistent structure for both successful and error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * Indicates whether the request was successful
     */
    private boolean success;
    
    /**
     * Response data (null on error)
     */
    private T data;
    
    /**
     * Error information (null on success)
     */
    private ErrorDetails error;
    
    /**
     * Request timestamp
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * Trace ID for request correlation
     */
    private String traceId;
    
    /**
     * Creates a successful response
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Creates a successful response with trace ID
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .traceId(traceId)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Creates an error response
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code(code)
                        .message(message)
                        .build())
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Creates an error response with trace ID
     */
    public static <T> ApiResponse<T> error(String code, String message, String traceId) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .code(code)
                        .message(message)
                        .build())
                .traceId(traceId)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Creates an error response with details
     */
    public static <T> ApiResponse<T> error(ErrorDetails errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(errorDetails)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Error details structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {
        /**
         * Error code (e.g., "XROAD_ERROR", "INVALID_REQUEST")
         */
        private String code;
        
        /**
         * Human-readable error message
         */
        private String message;
        
        /**
         * Additional error details (optional)
         */
        private String details;
        
        /**
         * Field-specific errors for validation failures (optional)
         */
        private Object fieldErrors;
    }
}
