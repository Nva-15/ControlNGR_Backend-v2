package com.example.ControlNGR.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.dto.SolicitudRequestDTO;
import com.example.ControlNGR.dto.SolicitudResponseDTO;
import com.example.ControlNGR.service.SolicitudService;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.JWTUtil;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    @Autowired
    private SolicitudService solicitudService;

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private JWTUtil jwtUtil;

    @PostMapping("/crear")
    public ResponseEntity<?> crearSolicitud(@RequestBody SolicitudRequestDTO request) {
        try {
            SolicitudResponseDTO response = solicitudService.crearSolicitud(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear solicitud: " + e.getMessage()));
        }
    }

    @PostMapping("/verificar-conflictos")
    public ResponseEntity<?> verificarConflictos(@RequestBody Map<String, Object> request) {
        try {
            Integer empleadoId = (Integer) request.get("empleadoId");
            String fechaInicioStr = (String) request.get("fechaInicio");
            String fechaFinStr = (String) request.get("fechaFin");
            
            if (empleadoId == null || fechaInicioStr == null || fechaFinStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Datos incompletos", "success", false));
            }
            
            java.time.LocalDate fechaInicio = java.time.LocalDate.parse(fechaInicioStr);
            java.time.LocalDate fechaFin = java.time.LocalDate.parse(fechaFinStr);
            
            List<com.example.ControlNGR.entity.Solicitud> conflictos = 
                solicitudService.verificarConflictosFecha(empleadoId, fechaInicio, fechaFin);
            
            boolean tieneConflictos = !conflictos.isEmpty();
            String mensaje = tieneConflictos ? 
                "⚠️ Ya existen " + conflictos.size() + " solicitud(es) para este período" :
                "✅ No hay conflictos de fecha";
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            List<Map<String, Object>> detallesConflictos = conflictos.stream()
                    .map(s -> {
                        Map<String, Object> detalle = new HashMap<>();
                        detalle.put("id", s.getId());
                        detalle.put("tipo", s.getTipo());
                        detalle.put("fechaInicio", s.getFechaInicio().format(formatter));
                        detalle.put("fechaFin", s.getFechaFin().format(formatter));
                        detalle.put("estado", s.getEstado());
                        detalle.put("motivo", s.getMotivo());
                        return detalle;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("tieneConflictos", tieneConflictos);
            respuesta.put("mensaje", mensaje);
            respuesta.put("totalConflictos", conflictos.size());
            respuesta.put("conflictos", detallesConflictos);
            respuesta.put("success", true);
            
            return ResponseEntity.ok(respuesta);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al verificar conflictos: " + e.getMessage(), "success", false));
        }
    }

    @PostMapping("/verificar-conflictos-por-rol")
    public ResponseEntity<?> verificarConflictosPorRol(@RequestBody Map<String, Object> request) {
        try {
            Integer empleadoId = (Integer) request.get("empleadoId");
            String rolEmpleado = (String) request.get("rolEmpleado");
            String fechaInicioStr = (String) request.get("fechaInicio");
            String fechaFinStr = (String) request.get("fechaFin");
            
            if (empleadoId == null || rolEmpleado == null || fechaInicioStr == null || fechaFinStr == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Datos incompletos", 
                    "success", false
                ));
            }
            
            java.time.LocalDate fechaInicio = java.time.LocalDate.parse(fechaInicioStr);
            java.time.LocalDate fechaFin = java.time.LocalDate.parse(fechaFinStr);
            
            List<com.example.ControlNGR.entity.Solicitud> conflictos = 
                solicitudService.verificarConflictosPorRolYFechas(empleadoId, rolEmpleado, fechaInicio, fechaFin);
            
            boolean tieneConflictos = !conflictos.isEmpty();
            String mensaje = tieneConflictos ? 
                "⚠️ Ya existe una solicitud en el rango de fechas seleccionado. La solicitud será evaluada." :
                "✅ No hay conflictos de fecha para el rol " + rolEmpleado;
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            List<Map<String, Object>> detallesConflictos = conflictos.stream()
                    .map(s -> {
                        Map<String, Object> detalle = new HashMap<>();
                        detalle.put("id", s.getId());
                        detalle.put("tipo", s.getTipo());
                        detalle.put("empleado", s.getEmpleado().getNombre());
                        detalle.put("rol", s.getEmpleado().getRol());
                        detalle.put("fechaInicio", s.getFechaInicio().format(formatter));
                        detalle.put("fechaFin", s.getFechaFin().format(formatter));
                        detalle.put("estado", s.getEstado());
                        return detalle;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("tieneConflictos", tieneConflictos);
            respuesta.put("mensaje", mensaje);
            respuesta.put("totalConflictos", conflictos.size());
            respuesta.put("rolVerificado", rolEmpleado);
            respuesta.put("conflictos", detallesConflictos);
            respuesta.put("success", true);
            
            return ResponseEntity.ok(respuesta);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al verificar conflictos: " + e.getMessage(), "success", false));
        }
    }

    @GetMapping("/mis-solicitudes/{empleadoId}")
    public ResponseEntity<List<SolicitudResponseDTO>> getMisSolicitudes(@PathVariable("empleadoId") Integer empleadoId) {
        return ResponseEntity.ok(solicitudService.obtenerMisSolicitudes(empleadoId));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<SolicitudResponseDTO>> getPendientes() {
        return ResponseEntity.ok(solicitudService.obtenerPendientes());
    }
    
    @GetMapping("/todas")
    public ResponseEntity<List<SolicitudResponseDTO>> getTodas() {
        return ResponseEntity.ok(solicitudService.obtenerTodas());
    }
    
    @GetMapping("/historial")
    public ResponseEntity<List<SolicitudResponseDTO>> getHistorial() {
        List<SolicitudResponseDTO> todas = solicitudService.obtenerTodas();
        List<SolicitudResponseDTO> historial = todas.stream()
                .filter(s -> !"pendiente".equals(s.getEstado()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(historial);
    }

    @PutMapping("/gestionar/{id}")
    public ResponseEntity<?> gestionarSolicitud(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, Object> payload) {
        try {
            String estado = (String) payload.get("estado");
            Integer idAprobador;
            
            if (payload.containsKey("empleadoId")) {
                idAprobador = (Integer) payload.get("empleadoId");
            } else if (payload.containsKey("usuarioId")) {
                idAprobador = (Integer) payload.get("usuarioId");
            } else if (payload.containsKey("aprobadorId")) {
                idAprobador = (Integer) payload.get("aprobadorId");
            } else if (payload.containsKey("idEmpleadoAprobador")) {
                idAprobador = (Integer) payload.get("idEmpleadoAprobador");
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "ID del aprobador requerido"));
            }
            
            String comentarios = (String) payload.get("comentarios");

            if (idAprobador == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "ID del aprobador requerido"));
            }

            if (!Arrays.asList("aprobado", "rechazado").contains(estado.toLowerCase())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Estado inválido. Solo se permite 'aprobado' o 'rechazado'"));
            }

            SolicitudResponseDTO response = solicitudService.gestionarSolicitud(id, estado, idAprobador, comentarios);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar la solicitud: " + e.getMessage()));
        }
    }
    
    @PutMapping("/editar/{id}")
    public ResponseEntity<?> editarSolicitud(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, Object> payload,
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No autorizado"));
            }
            
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            var empleadoOpt = empleadoService.findByUsername(username);
            
            if (!empleadoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Empleado no encontrado"));
            }
            
            var empleado = empleadoOpt.get();
            Integer empleadoEditorId = empleado.getId();
            String rolEditor = empleado.getRol();
            
            Map<String, Object> datosEdicion = new HashMap<>();
            
            if (payload.containsKey("fechaInicio")) {
                datosEdicion.put("fechaInicio", payload.get("fechaInicio"));
            }
            
            if (payload.containsKey("fechaFin")) {
                datosEdicion.put("fechaFin", payload.get("fechaFin"));
            }
            
            if (payload.containsKey("tipo")) {
                datosEdicion.put("tipo", payload.get("tipo"));
            }
            
            if (payload.containsKey("motivo")) {
                datosEdicion.put("motivo", payload.get("motivo"));
            }
            
            if (payload.containsKey("estado")) {
                datosEdicion.put("estado", payload.get("estado"));
            }
            
            SolicitudResponseDTO response = solicitudService.editarSolicitud(
                id, datosEdicion, empleadoEditorId, rolEditor);
            
            return ResponseEntity.ok(Map.of(
                "solicitud", response,
                "message", "Solicitud editada correctamente",
                "success", true
            ));
            
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al editar solicitud: " + e.getMessage(), "success", false));
        }
    }
    
    @GetMapping("/exportar/{tipo}")
    public ResponseEntity<?> exportarSolicitudes(
            @PathVariable("tipo") String tipoReporte,
            @RequestParam(value = "empleadoId", required = false) Integer empleadoId,
            @RequestParam(value = "formato", defaultValue = "json") String formato) {
        try {
            Map<String, Object> reporte = solicitudService.exportarSolicitudes(tipoReporte, empleadoId);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(reporte);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al exportar: " + e.getMessage()));
        }
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminarSolicitud(
            @PathVariable("id") Integer id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No autorizado", "success", false));
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            var empleadoOpt = empleadoService.findByUsername(username);

            if (!empleadoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Empleado no encontrado", "success", false));
            }

            Integer empleadoId = empleadoOpt.get().getId();

            solicitudService.eliminarSolicitud(id, empleadoId);

            return ResponseEntity.ok(Map.of(
                "message", "Solicitud eliminada correctamente",
                "success", true
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar solicitud: " + e.getMessage(), "success", false));
        }
    }
}