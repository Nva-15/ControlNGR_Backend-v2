package com.example.ControlNGR.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.dto.*;
import com.example.ControlNGR.service.EventoService;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.JWTUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    @Autowired
    private EventoService eventoService;

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private JWTUtil jwtUtil;

    // ==================== CRUD EVENTOS ====================

    @PostMapping("/crear")
    public ResponseEntity<?> crearEvento(
            @RequestBody EventoRequestDTO request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            request.setCreadoPorId(empleadoId);

            EventoResponseDTO response = eventoService.crearEvento(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear evento: " + e.getMessage()));
        }
    }

    @PutMapping("/actualizar/{id}")
    public ResponseEntity<?> actualizarEvento(
            @PathVariable("id") Integer id,
            @RequestBody EventoRequestDTO request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);

            EventoResponseDTO response = eventoService.actualizarEvento(id, request, empleadoId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar evento: " + e.getMessage()));
        }
    }

    @PutMapping("/estado/{id}")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            String nuevoEstado = payload.get("estado");

            if (nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Estado requerido"));
            }

            EventoResponseDTO response = eventoService.cambiarEstado(id, nuevoEstado, empleadoId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al cambiar estado: " + e.getMessage()));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminarEvento(
            @PathVariable("id") Integer id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);

            eventoService.eliminarEvento(id, empleadoId);
            return ResponseEntity.ok(Map.of(
                "message", "Evento eliminado correctamente",
                "success", true
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar evento: " + e.getMessage(), "success", false));
        }
    }

    // ==================== CONSULTAS ====================

    @GetMapping("/activos")
    public ResponseEntity<?> obtenerEventosActivos(@RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            List<EventoResponseDTO> eventos = eventoService.obtenerEventosActivos(empleadoId);
            return ResponseEntity.ok(eventos);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener eventos: " + e.getMessage()));
        }
    }

    @GetMapping("/proximos")
    public ResponseEntity<?> obtenerProximosEventos(@RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            List<EventoResponseDTO> eventos = eventoService.obtenerProximosEventos(empleadoId);
            return ResponseEntity.ok(eventos);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener eventos: " + e.getMessage()));
        }
    }

    @GetMapping("/todos")
    public ResponseEntity<?> obtenerTodosEventos(@RequestHeader("Authorization") String authHeader) {
        try {
            // Verificar que es admin/supervisor
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            var empleado = empleadoService.findById(empleadoId);

            if (!empleado.isPresent() ||
                !java.util.Arrays.asList("admin", "supervisor").contains(empleado.get().getRol().toLowerCase())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Sin permisos para ver todos los eventos"));
            }

            List<EventoResponseDTO> eventos = eventoService.obtenerTodosEventos();
            return ResponseEntity.ok(eventos);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener eventos: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerEventoPorId(
            @PathVariable("id") Integer id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            EventoResponseDTO evento = eventoService.obtenerEventoPorId(id, empleadoId);
            return ResponseEntity.ok(evento);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener evento: " + e.getMessage()));
        }
    }

    // ==================== RESPUESTAS ====================

    @PostMapping("/responder")
    public ResponseEntity<?> responderEvento(
            @RequestBody RespuestaEventoRequestDTO request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            request.setEmpleadoId(empleadoId);

            RespuestaEventoResponseDTO response = eventoService.responderEvento(request);
            return ResponseEntity.ok(Map.of(
                "respuesta", response,
                "message", "Respuesta registrada correctamente",
                "success", true
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al registrar respuesta: " + e.getMessage(), "success", false));
        }
    }

    @GetMapping("/{id}/respuestas")
    public ResponseEntity<?> obtenerRespuestas(
            @PathVariable("id") Integer eventoId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Verificar autenticación
            obtenerEmpleadoIdDesdeToken(authHeader);

            List<RespuestaEventoResponseDTO> respuestas = eventoService.obtenerRespuestasEvento(eventoId);
            return ResponseEntity.ok(respuestas);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener respuestas: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/mi-respuesta")
    public ResponseEntity<?> obtenerMiRespuesta(
            @PathVariable("id") Integer eventoId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);

            Optional<RespuestaEventoResponseDTO> respuesta = eventoService.obtenerMiRespuesta(eventoId, empleadoId);
            if (respuesta.isPresent()) {
                return ResponseEntity.ok(respuesta.get());
            } else {
                return ResponseEntity.ok(Map.of("yaRespondio", false));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener respuesta: " + e.getMessage()));
        }
    }

    // ==================== COMENTARIOS ====================

    @PostMapping("/{id}/comentarios")
    public ResponseEntity<?> agregarComentario(
            @PathVariable("id") Integer eventoId,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer empleadoId = obtenerEmpleadoIdDesdeToken(authHeader);
            String comentario = payload.get("comentario");

            if (comentario == null || comentario.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Comentario requerido"));
            }

            ComentarioEventoDTO response = eventoService.agregarComentario(eventoId, empleadoId, comentario);
            return ResponseEntity.ok(Map.of(
                "comentario", response,
                "message", "Comentario agregado correctamente",
                "success", true
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al agregar comentario: " + e.getMessage(), "success", false));
        }
    }

    @GetMapping("/{id}/comentarios")
    public ResponseEntity<?> obtenerComentarios(
            @PathVariable("id") Integer eventoId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Verificar autenticación
            obtenerEmpleadoIdDesdeToken(authHeader);

            List<ComentarioEventoDTO> comentarios = eventoService.obtenerComentarios(eventoId);
            return ResponseEntity.ok(comentarios);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener comentarios: " + e.getMessage()));
        }
    }

    // ==================== ESTADISTICAS ====================

    @GetMapping("/{id}/estadisticas")
    public ResponseEntity<?> obtenerEstadisticas(
            @PathVariable("id") Integer eventoId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            // Verificar autenticación
            obtenerEmpleadoIdDesdeToken(authHeader);

            EstadisticasEventoDTO estadisticas = eventoService.obtenerEstadisticas(eventoId);
            return ResponseEntity.ok(estadisticas);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener estadisticas: " + e.getMessage()));
        }
    }

    // ==================== UTILIDADES ====================

    private Integer obtenerEmpleadoIdDesdeToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("No autorizado");
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        var empleadoOpt = empleadoService.findByUsername(username);
        if (!empleadoOpt.isPresent()) {
            throw new RuntimeException("Empleado no encontrado");
        }

        return empleadoOpt.get().getId();
    }
}
