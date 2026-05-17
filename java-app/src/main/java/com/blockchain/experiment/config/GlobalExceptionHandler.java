package com.blockchain.experiment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.ConnectException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for REST APIs.
 * Handles common blockchain-related errors (RPC failures, connection issues).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConnectException.class)
    public ResponseEntity<Map<String, Object>> handleConnectionException(ConnectException e) {
        logger.error("Blockchain node connection failed: {}", e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Blockchain node connection failed");
        body.put("detail", e.getMessage());
        body.put("suggestion", "Check if the Ethereum nodes are running (docker-compose up -d)");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        logger.error("Runtime error: {}", e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());

        // Detect RPC-related errors
        if (e.getMessage() != null && (
                e.getMessage().contains("Connection refused") ||
                e.getMessage().contains("Failed to connect") ||
                e.getMessage().contains("timeout"))) {
            body.put("suggestion", "Ethereum RPC node may be down. Check docker containers.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        logger.error("Unexpected error: ", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Internal server error");
        body.put("detail", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
