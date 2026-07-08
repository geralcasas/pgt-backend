package com.portable.microservices.ms_inventory.locations.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.hc.core5.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.portable.shared.infrastructure.presentation.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
// 2. Atrapa los errores de negocio que nosotros lanzamos a propósito (Ej: "Almacén no existe")
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.SC_BAD_REQUEST) // Código 400
                // Usamos nuestro método de error. Pasamos null en la data porque no hay datos que devolver.
                .body(ApiResponse.error(ex.getMessage(), null)); 
    }

    // 3. Atrapa los errores de los DTOs cuando fallan los @NotBlank o @NotNull
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        // Extraemos qué campo falló y su mensaje
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        
        return ResponseEntity
                .status(HttpStatus.SC_BAD_REQUEST) // Código 400
                // Aquí sí usamos la 'data' del error para mandarle la lista de campos fallidos al Frontend
                .body(ApiResponse.error("Error en la validación de los datos enviados", errors));
    }

    // 4. Errores de validación de negocio (capacidad, stock, etc.) — 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.SC_BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    // 5. El "Atrapa-Todo" para errores inesperados (NullPointers, BD caída, etc.)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Ojo: En un entorno real de producción, no deberías exponer ex.getMessage() al usuario, 
        // pero para desarrollo es muy útil para saber qué se rompió.
        return ResponseEntity
                .status(HttpStatus.SC_SERVER_ERROR)
                .body(ApiResponse.error("Ocurrió un error interno: " + ex.getMessage(), null));
    }
}
