package com.example.ControlNGR.controller;

import com.example.ControlNGR.dto.DetalleHorarioDiaDTO;
import com.example.ControlNGR.dto.HorarioSemanalRequestDTO;
import com.example.ControlNGR.dto.HorarioSemanalResponseDTO;
import com.example.ControlNGR.service.HorarioSemanalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/horarios-semanales")
public class HorarioSemanalController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.ControlNGR.security.UsuarioActual usuarioActual;

    private static final Logger logger = LoggerFactory.getLogger(HorarioSemanalController.class);

    @Autowired
    private HorarioSemanalService horarioSemanalService;

    // ==================== TEST ====================

    /**
     * Endpoint de prueba para verificar que el controlador funciona
     * GET /api/horarios-semanales/test
     */
    @GetMapping("/test")
    public ResponseEntity<?> test() {
        logger.info("Endpoint de prueba llamado");
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "El controlador de horarios semanales funciona correctamente",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    // ==================== CREAR ====================

    /**
     * Generar una nueva semana de horarios
     * POST /api/horarios-semanales/generar
     * Body: { fechaInicio, fechaFin, creadoPorId, copiarDeId? }
     */
    @PostMapping("/generar")
    public ResponseEntity<?> generarSemana(@RequestBody HorarioSemanalRequestDTO request) {
        logger.info("Recibida solicitud para generar semana: fechaInicio={}, fechaFin={}, creadoPorId={}, copiarDeId={}",
                request.getFechaInicio(), request.getFechaFin(), request.getCreadoPorId(), request.getCopiarDeId());

        try {
            // Validaciones previas
            if (request.getFechaInicio() == null) {
                logger.warn("Fecha de inicio es null");
                return ResponseEntity.badRequest().body(Map.of("error", "La fecha de inicio es requerida"));
            }
            if (request.getFechaFin() == null) {
                logger.warn("Fecha de fin es null");
                return ResponseEntity.badRequest().body(Map.of("error", "La fecha de fin es requerida"));
            }
            // El creador es el usuario autenticado (null si es el administrador del sistema)
            com.example.ControlNGR.entity.Empleado creador = usuarioActual.requerido().getEmpleado();
            request.setCreadoPorId(creador != null ? creador.getId() : null);

            HorarioSemanalResponseDTO response = horarioSemanalService.generarSemana(request);
            logger.info("Semana generada exitosamente con ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            logger.error("Error al generar semana: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error inesperado al generar semana: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor: " + e.getMessage()));
        }
    }

    /**
     * Copiar una semana existente a nuevas fechas
     * POST /api/horarios-semanales/{id}/copiar?nuevaFechaInicio=2025-02-03
     */
    @PostMapping("/{id}/copiar")
    public ResponseEntity<?> copiarSemana(
            @PathVariable("id") Integer id,
            @RequestParam("nuevaFechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate nuevaFechaInicio) {
        try {
            HorarioSemanalResponseDTO response = horarioSemanalService.copiarSemana(id, nuevaFechaInicio);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== CONSULTAR ====================

    /**
     * Listar todas las semanas
     * GET /api/horarios-semanales
     */
    @GetMapping
    public ResponseEntity<?> listarTodas() {
        try {
            logger.info("Listando todas las semanas de horarios");
            List<HorarioSemanalResponseDTO> semanas = horarioSemanalService.obtenerTodas();
            logger.info("Se encontraron {} semanas", semanas.size());
            return ResponseEntity.ok(semanas);
        } catch (Exception e) {
            logger.error("Error al listar semanas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al listar semanas: " + e.getMessage()));
        }
    }

    /**
     * Obtener semana por ID
     * GET /api/horarios-semanales/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable("id") Integer id) {
        try {
            HorarioSemanalResponseDTO response = horarioSemanalService.obtenerPorId(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener semana vigente (la que contiene la fecha actual)
     * GET /api/horarios-semanales/vigente
     */
    @GetMapping("/vigente")
    public ResponseEntity<?> obtenerVigente() {
        try {
            HorarioSemanalResponseDTO response = horarioSemanalService.obtenerVigente();
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener semana que contiene una fecha específica
     * GET /api/horarios-semanales/por-fecha?fecha=2025-01-28
     */
    @GetMapping("/por-fecha")
    public ResponseEntity<?> obtenerPorFecha(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        try {
            HorarioSemanalResponseDTO response = horarioSemanalService.obtenerPorFecha(fecha);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtener mi horario de la semana activa (para el dashboard del empleado)
     * GET /api/horarios-semanales/mi-horario/{empleadoId}
     */
    @GetMapping("/mi-horario/{empleadoId}")
    public ResponseEntity<?> obtenerMiHorarioVigente(@PathVariable("empleadoId") Integer empleadoId) {
        try {
            var response = horarioSemanalService.obtenerMiHorarioVigente(empleadoId);
            if (response == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No hay horario semanal activo para la fecha actual"));
            }
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== ACTUALIZAR ====================

    /**
     * Actualizar un día específico del horario
     * PUT /api/horarios-semanales/detalle/{detalleId}
     * Body: { horaEntrada, horaSalida, tipoDia, turno, ... }
     */
    @PutMapping("/detalle/{detalleId}")
    public ResponseEntity<?> actualizarDetalle(
            @PathVariable("detalleId") Integer detalleId,
            @RequestBody DetalleHorarioDiaDTO request) {
        try {
            HorarioSemanalResponseDTO response = horarioSemanalService.actualizarDetalle(detalleId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Actualizar múltiples días con el mismo horario
     * PUT /api/horarios-semanales/detalle/multiple
     * Body: { detalleIds: [1,2,3], horaEntrada, horaSalida, tipoDia, turno, ... }
     */
    @PutMapping("/detalle/multiple")
    public ResponseEntity<?> actualizarMultiplesDetalles(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Integer> detalleIds = (List<Integer>) request.get("detalleIds");

            if (detalleIds == null || detalleIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Debe seleccionar al menos un día"));
            }

            DetalleHorarioDiaDTO datos = new DetalleHorarioDiaDTO();
            datos.setHoraEntrada((String) request.get("horaEntrada"));
            datos.setHoraSalida((String) request.get("horaSalida"));
            datos.setHoraAlmuerzoInicio((String) request.get("horaAlmuerzoInicio"));
            datos.setHoraAlmuerzoFin((String) request.get("horaAlmuerzoFin"));
            datos.setTipoDia((String) request.get("tipoDia"));
            datos.setTurno((String) request.get("turno"));

            HorarioSemanalResponseDTO response = horarioSemanalService.actualizarMultiplesDetalles(detalleIds, datos);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Error al actualizar múltiples detalles: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cambiar el estado de una semana
     * PUT /api/horarios-semanales/{id}/estado
     * Body: { estado: "activo" | "borrador" | "historico" }
     */
    @PutMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, String> body) {
        try {
            String nuevoEstado = body.get("estado");
            if (nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El estado es requerido"));
            }
            HorarioSemanalResponseDTO response = horarioSemanalService.cambiarEstado(id, nuevoEstado);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== ELIMINAR ====================

    /**
     * Eliminar una semana (solo si está en estado borrador)
     * DELETE /api/horarios-semanales/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarSemana(@PathVariable("id") Integer id) {
        try {
            horarioSemanalService.eliminarSemana(id);
            return ResponseEntity.ok(Map.of("message", "Semana eliminada correctamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
