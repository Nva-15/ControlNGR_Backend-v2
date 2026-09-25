package com.example.ControlNGR.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.ControlNGR.dto.SolicitudRequestDTO;
import com.example.ControlNGR.dto.SolicitudResponseDTO;
import com.example.ControlNGR.entity.Solicitud;
import com.example.ControlNGR.entity.SolicitudEvidencia;
import com.example.ControlNGR.service.SolicitudService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SolicitudService solicitudService;

    public SolicitudController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    /** Crear solicitud sin archivo (vacaciones, compensacion). */
    @PostMapping(value = "/crear", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SolicitudResponseDTO> crearSolicitud(@RequestBody SolicitudRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crearSolicitud(request, null));
    }

    /**
     * Crear solicitud con evidencia (descanso medico, licencia).
     * multipart/form-data con la parte "solicitud" (JSON) y la parte "archivo" (foto o PDF).
     */
    @PostMapping(value = "/crear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SolicitudResponseDTO> crearSolicitudConEvidencia(
            @RequestPart("solicitud") SolicitudRequestDTO request,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crearSolicitud(request, archivo));
    }

    /** Catalogo de tipos de solicitud activos. */
    @GetMapping("/tipos")
    public ResponseEntity<?> tipos() {
        return ResponseEntity.ok(solicitudService.tiposActivos());
    }

    /** Catalogo de motivos de licencia. */
    @GetMapping("/motivos-licencia")
    public ResponseEntity<?> motivosLicencia() {
        return ResponseEntity.ok(solicitudService.motivosLicencia());
    }

    @PostMapping("/verificar-conflictos")
    public ResponseEntity<?> verificarConflictos(@RequestBody Map<String, Object> request) {
        Integer empleadoId = (Integer) request.get("empleadoId");
        String fechaInicioStr = (String) request.get("fechaInicio");
        String fechaFinStr = (String) request.get("fechaFin");
        if (empleadoId == null || fechaInicioStr == null || fechaFinStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos incompletos", "success", false));
        }
        List<Solicitud> conflictos = solicitudService.verificarConflictosFecha(
                empleadoId, LocalDate.parse(fechaInicioStr), LocalDate.parse(fechaFinStr));

        List<Map<String, Object>> detalles = conflictos.stream().map(s -> {
            Map<String, Object> d = new HashMap<>();
            d.put("id", s.getId());
            d.put("tipo", s.getTipo());
            d.put("fechaInicio", s.getFechaInicio().format(ISO));
            d.put("fechaFin", s.getFechaFin().format(ISO));
            d.put("estado", s.getEstado());
            d.put("motivo", s.getMotivo());
            return d;
        }).toList();

        boolean tieneConflictos = !conflictos.isEmpty();
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tieneConflictos", tieneConflictos);
        respuesta.put("mensaje", tieneConflictos
                ? "⚠️ Ya existen " + conflictos.size() + " solicitud(es) para este período"
                : "✅ No hay conflictos de fecha");
        respuesta.put("totalConflictos", conflictos.size());
        respuesta.put("conflictos", detalles);
        respuesta.put("success", true);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/verificar-conflictos-por-rol")
    public ResponseEntity<?> verificarConflictosPorRol(@RequestBody Map<String, Object> request) {
        Integer empleadoId = (Integer) request.get("empleadoId");
        String rolEmpleado = (String) request.get("rolEmpleado");
        String fechaInicioStr = (String) request.get("fechaInicio");
        String fechaFinStr = (String) request.get("fechaFin");
        if (empleadoId == null || rolEmpleado == null || fechaInicioStr == null || fechaFinStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Datos incompletos", "success", false));
        }
        List<Solicitud> conflictos = solicitudService.verificarConflictosPorRolYFechas(
                empleadoId, rolEmpleado, LocalDate.parse(fechaInicioStr), LocalDate.parse(fechaFinStr));

        List<Map<String, Object>> detalles = conflictos.stream().map(s -> {
            Map<String, Object> d = new HashMap<>();
            d.put("id", s.getId());
            d.put("tipo", s.getTipo());
            d.put("empleado", s.getEmpleado().getNombre());
            d.put("rol", s.getEmpleado().getRol());
            d.put("fechaInicio", s.getFechaInicio().format(ISO));
            d.put("fechaFin", s.getFechaFin().format(ISO));
            d.put("estado", s.getEstado());
            return d;
        }).toList();

        boolean tieneConflictos = !conflictos.isEmpty();
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("tieneConflictos", tieneConflictos);
        respuesta.put("mensaje", tieneConflictos
                ? "⚠️ Ya existe una solicitud en el rango de fechas seleccionado. La solicitud será evaluada."
                : "✅ No hay conflictos de fecha para el rol " + rolEmpleado);
        respuesta.put("totalConflictos", conflictos.size());
        respuesta.put("rolVerificado", rolEmpleado);
        respuesta.put("conflictos", detalles);
        respuesta.put("success", true);
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/mis-solicitudes/{empleadoId}")
    public ResponseEntity<List<SolicitudResponseDTO>> getMisSolicitudes(@PathVariable("empleadoId") Integer empleadoId) {
        return ResponseEntity.ok(solicitudService.obtenerMisSolicitudes(empleadoId));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<SolicitudResponseDTO>> getPendientes() {
        return ResponseEntity.ok(solicitudService.obtenerPendientes());
    }

    /** Solo las pendientes que el usuario autenticado puede aprobar segun su rol. */
    @GetMapping("/pendientes-por-aprobar")
    public ResponseEntity<List<SolicitudResponseDTO>> getPendientesPorAprobar() {
        return ResponseEntity.ok(solicitudService.obtenerPendientesPorAprobar());
    }

    @GetMapping("/todas")
    public ResponseEntity<List<SolicitudResponseDTO>> getTodas() {
        return ResponseEntity.ok(solicitudService.obtenerTodas());
    }

    @GetMapping("/historial")
    public ResponseEntity<List<SolicitudResponseDTO>> getHistorial() {
        return ResponseEntity.ok(solicitudService.obtenerTodas().stream()
                .filter(s -> !Solicitud.PENDIENTE.equals(s.getEstado())).toList());
    }

    /** Historial de cambios de estado de una solicitud. */
    @GetMapping("/{id}/historial")
    public ResponseEntity<?> getHistorialSolicitud(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(solicitudService.historial(id));
    }

    /** Descarga la evidencia adjunta (solicitante, aprobadores, gerencia y admin). */
    @GetMapping("/{id}/evidencias/{evidenciaId}")
    public ResponseEntity<Resource> descargarEvidencia(@PathVariable("id") Integer id,
                                                       @PathVariable("evidenciaId") Integer evidenciaId) {
        Map.Entry<SolicitudEvidencia, Resource> e = solicitudService.obtenerEvidencia(id, evidenciaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(e.getKey().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(e.getKey().getNombreOriginal(), StandardCharsets.UTF_8).build().toString())
                .body(e.getValue());
    }

    /**
     * Aprobar o rechazar. El aprobador es siempre el usuario autenticado
     * (los campos empleadoId/aprobadorId del cuerpo se ignoran).
     */
    @PutMapping("/gestionar/{id}")
    public ResponseEntity<SolicitudResponseDTO> gestionarSolicitud(@PathVariable("id") Integer id,
                                                                   @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(solicitudService.gestionarSolicitud(
                id, (String) payload.get("estado"), (String) payload.get("comentarios")));
    }

    @PutMapping("/editar/{id}")
    public ResponseEntity<?> editarSolicitud(@PathVariable("id") Integer id, @RequestBody Map<String, Object> payload) {
        SolicitudResponseDTO response = solicitudService.editarSolicitud(id, payload);
        return ResponseEntity.ok(Map.of(
            "solicitud", response,
            "message", "Solicitud editada correctamente",
            "success", true
        ));
    }

    @GetMapping("/exportar/{tipo}")
    public ResponseEntity<?> exportarSolicitudes(
            @PathVariable("tipo") String tipoReporte,
            @RequestParam(value = "empleadoId", required = false) Integer empleadoId,
            @RequestParam(value = "formato", defaultValue = "json") String formato) {
        return ResponseEntity.ok(solicitudService.exportarSolicitudes(tipoReporte, empleadoId));
    }

    @DeleteMapping("/eliminar/{id}")
    public ResponseEntity<?> eliminarSolicitud(@PathVariable("id") Integer id) {
        solicitudService.eliminarSolicitud(id);
        return ResponseEntity.ok(Map.of("message", "Solicitud eliminada correctamente", "success", true));
    }
}
