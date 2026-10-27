package com.example.ControlNGR.controller;

import com.example.ControlNGR.service.ReporteAsistenciaService;
import com.example.ControlNGR.service.ReporteAsistenciaService.Filtro;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Dashboard de asistencia y ficha del colaborador (jefaturas) y "Mi récord" (cada colaborador, si el panel lo habilita).
 * Los permisos y el alcance por equipo los valida el servicio.
 */
@RestController
@RequestMapping("/api/reportes-asistencia")
public class ReporteAsistenciaController {

    private final ReporteAsistenciaService servicio;

    public ReporteAsistenciaController(ReporteAsistenciaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestParam("desde") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam("hasta") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(value = "departamentoId", required = false) Integer departamentoId,
            @RequestParam(value = "rol", required = false) String rol,
            @RequestParam(value = "turno", required = false) String turno,
            @RequestParam(value = "personal", defaultValue = "activos") String personal,
            @RequestParam(value = "empleadoId", required = false) Integer empleadoId) {
        return ResponseEntity.ok(servicio.dashboard(new Filtro(desde, hasta, departamentoId, rol, turno, personal, empleadoId)));
    }

    /** Ficha de un colaborador; sin "desde", desde su fecha de ingreso. */
    @GetMapping("/ficha/{empleadoId}")
    public ResponseEntity<Map<String, Object>> ficha(
            @PathVariable("empleadoId") Integer empleadoId,
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(servicio.ficha(empleadoId, desde, hasta));
    }

    /** Récord propio: solo sus datos, solo lectura. */
    @GetMapping("/mi-record")
    public ResponseEntity<Map<String, Object>> miRecord() {
        return ResponseEntity.ok(servicio.miRecord());
    }
}
