package com.example.ControlNGR.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.dto.HorarioRequestDTO;
import com.example.ControlNGR.dto.HorarioResponseDTO;
import com.example.ControlNGR.dto.HorarioSemanalDTO;
import com.example.ControlNGR.service.HorarioService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/horarios")
public class HorarioController {

    @Autowired
    private HorarioService horarioService;

    @GetMapping
    public ResponseEntity<List<HorarioResponseDTO>> obtenerTodos() {
        return ResponseEntity.ok(horarioService.obtenerTodos());
    }

    // Vista consolidada de todos los empleados con sus horarios semanales
    @GetMapping("/consolidado")
    public ResponseEntity<List<HorarioSemanalDTO>> obtenerVistaConsolidada(
            @RequestParam(value = "rol", required = false) String rol) {
        return ResponseEntity.ok(horarioService.obtenerVistaConsolidada(rol));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable("id") Integer id) {
        try {
            return ResponseEntity.ok(horarioService.obtenerPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/empleado/{empleadoId}")
    public ResponseEntity<?> obtenerPorEmpleado(@PathVariable("empleadoId") Integer empleadoId) {
        try {
            return ResponseEntity.ok(horarioService.obtenerPorEmpleado(empleadoId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Obtener vista consolidada semanal de un empleado individual
    @GetMapping("/empleado/{empleadoId}/semanal")
    public ResponseEntity<?> obtenerHorarioSemanalEmpleado(@PathVariable("empleadoId") Integer empleadoId) {
        try {
            return ResponseEntity.ok(horarioService.obtenerHorarioSemanalEmpleado(empleadoId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> crearHorario(@RequestBody HorarioRequestDTO request) {
        try {
            HorarioResponseDTO response = horarioService.crearHorario(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear horario: " + e.getMessage()));
        }
    }

    // Crear o actualizar horario de un dia especifico para un empleado
    @PutMapping("/empleado/{empleadoId}/dia/{diaSemana}")
    public ResponseEntity<?> crearOActualizarHorarioDia(
            @PathVariable("empleadoId") Integer empleadoId,
            @PathVariable("diaSemana") String diaSemana,
            @RequestBody HorarioRequestDTO request) {
        try {
            HorarioResponseDTO response = horarioService.crearOActualizarHorarioDia(empleadoId, diaSemana, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al guardar horario: " + e.getMessage()));
        }
    }

    // Aplicar el mismo horario a multiples dias de un empleado
    @PutMapping("/empleado/{empleadoId}/dias-multiples")
    public ResponseEntity<?> aplicarHorarioMultiplesDias(
            @PathVariable("empleadoId") Integer empleadoId,
            @RequestBody HorarioRequestDTO request) {
        try {
            if (request.getDias() == null || request.getDias().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Debe especificar al menos un dia"));
            }

            List<HorarioResponseDTO> response = horarioService.aplicarHorarioMultiplesDias(
                    empleadoId, request.getDias(), request);

            return ResponseEntity.ok(Map.of(
                    "message", "Horario aplicado a " + request.getDias().size() + " dia(s) correctamente",
                    "horarios", response
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al aplicar horario: " + e.getMessage()));
        }
    }

    @PostMapping("/semana/{empleadoId}")
    public ResponseEntity<?> crearHorariosSemana(
            @PathVariable("empleadoId") Integer empleadoId,
            @RequestBody HorarioRequestDTO plantilla) {
        try {
            List<HorarioResponseDTO> response = horarioService.crearHorariosSemana(empleadoId, plantilla);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Horarios de semana creados correctamente",
                    "horarios", response
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear horarios: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarHorario(
            @PathVariable("id") Integer id,
            @RequestBody HorarioRequestDTO request) {
        try {
            HorarioResponseDTO response = horarioService.actualizarHorario(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar horario: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarHorario(@PathVariable("id") Integer id) {
        try {
            horarioService.eliminarHorario(id);
            return ResponseEntity.ok(Map.of("message", "Horario eliminado correctamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar horario: " + e.getMessage()));
        }
    }

    @DeleteMapping("/empleado/{empleadoId}")
    public ResponseEntity<?> eliminarHorariosPorEmpleado(@PathVariable("empleadoId") Integer empleadoId) {
        try {
            horarioService.eliminarHorariosPorEmpleado(empleadoId);
            return ResponseEntity.ok(Map.of("message", "Horarios del empleado eliminados correctamente"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar horarios: " + e.getMessage()));
        }
    }
}
