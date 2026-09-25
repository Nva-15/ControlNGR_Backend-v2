package com.example.ControlNGR.config;

import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.FueraDeRedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/** Respuestas de error uniformes: {"error": "...", "success": false}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FueraDeRedException.class)
    public ResponseEntity<?> fueraDeRed(FueraDeRedException e) {
        logger.warn("Marcacion rechazada fuera de red desde {}", e.getIp());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", e.getMessage(), "fueraDeRed", true, "ip", String.valueOf(e.getIp()), "success", false));
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<?> accesoDenegado(AccesoDenegadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage(), "success", false));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> negocio(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> archivoGrande(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "El archivo supera el tamaño máximo permitido", "success", false));
    }
}
