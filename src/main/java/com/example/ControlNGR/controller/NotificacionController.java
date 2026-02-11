package com.example.ControlNGR.controller;

import com.example.ControlNGR.dto.NotificacionResumenDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.JWTUtil;
import com.example.ControlNGR.service.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NotificacionController.class);

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private EmpleadoService empleadoService;

    @GetMapping("/resumen")
    public ResponseEntity<?> obtenerResumen(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No autorizado"));
            }

            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Token invalido"));
            }

            String username = jwtUtil.extractUsername(token);
            Optional<Empleado> empleadoOpt = empleadoService.findByUsername(username);

            if (!empleadoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Usuario no encontrado"));
            }

            NotificacionResumenDTO resumen = notificacionService.obtenerResumen(empleadoOpt.get());
            return ResponseEntity.ok(resumen);

        } catch (Exception e) {
            logger.error("Error obteniendo resumen de notificaciones: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener notificaciones"));
        }
    }
}
