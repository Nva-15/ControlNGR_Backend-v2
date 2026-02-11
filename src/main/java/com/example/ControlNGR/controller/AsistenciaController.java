package com.example.ControlNGR.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.dto.AsistenciaRequestDTO;
import com.example.ControlNGR.dto.AsistenciaResponseDTO;
import com.example.ControlNGR.dto.ReporteAsistenciaDTO;
import com.example.ControlNGR.service.AsistenciaService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asistencia")
public class AsistenciaController {
    
    @Autowired
    private AsistenciaService asistenciaService;
    
    @PostMapping("/registrar")
    public ResponseEntity<?> registrarAsistencia(@RequestBody AsistenciaRequestDTO request) {
        try {
            AsistenciaResponseDTO response = asistenciaService.registrarAsistencia(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al registrar asistencia: " + e.getMessage()));
        }
    }
    
    @GetMapping
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerTodasAsistencias() {
        List<AsistenciaResponseDTO> asistencias = asistenciaService.obtenerTodasAsistencias();
        return ResponseEntity.ok(asistencias);
    }
    
    // CORREGIDO: Se agregó ("empleadoId")
    @GetMapping("/empleado/{empleadoId}")
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerAsistenciasPorEmpleado(
            @PathVariable("empleadoId") Integer empleadoId) {
        List<AsistenciaResponseDTO> asistencias = asistenciaService.obtenerAsistenciasPorEmpleado(empleadoId);
        return ResponseEntity.ok(asistencias);
    }
    
    // CORREGIDO: Se agregó ("fecha")
    @GetMapping("/fecha/{fecha}")
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerAsistenciasPorFecha(
            @PathVariable("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        List<AsistenciaResponseDTO> asistencias = asistenciaService.obtenerAsistenciasPorFecha(fecha);
        return ResponseEntity.ok(asistencias);
    }
    
    // CORREGIDO: Se agregó ("inicio") y ("fin")
    @GetMapping("/rango")
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerAsistenciasPorRango(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        List<AsistenciaResponseDTO> asistencias = asistenciaService.obtenerAsistenciasPorRango(inicio, fin);
        return ResponseEntity.ok(asistencias);
    }
    
    // CORREGIDO: Se agregaron los nombres explícitos
    @GetMapping("/reporte/mensual/{empleadoId}")
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerReporteMensual(
            @PathVariable("empleadoId") Integer empleadoId,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        List<AsistenciaResponseDTO> asistencias = asistenciaService.obtenerReporteMensual(empleadoId, year, month);
        return ResponseEntity.ok(asistencias);
    }
    
    @GetMapping("/hoy")
    public ResponseEntity<List<AsistenciaResponseDTO>> obtenerAsistenciaHoy() {
        LocalDate hoy = LocalDate.now();
        List<AsistenciaResponseDTO> asistencias = asistenciaService.obtenerAsistenciasPorFecha(hoy);
        return ResponseEntity.ok(asistencias);
    }
    
    @GetMapping("/reporte/rango")
    public ResponseEntity<?> obtenerReporteAsistencia(
            @RequestParam("inicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam("fin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        try {
            List<ReporteAsistenciaDTO> reporte = asistenciaService.generarReporteAsistencia(inicio, fin);
            return ResponseEntity.ok(reporte);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al generar reporte: " + e.getMessage()));
        }
    }

    @PostMapping("/verificar-salidas")
    public ResponseEntity<?> verificarSalidasAutomaticas() {
        try {
            asistenciaService.verificarSalidasAutomaticas();
            return ResponseEntity.ok(Map.of("message", "Verificación de salidas automáticas completada"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error en verificación: " + e.getMessage()));
        }
    }
}