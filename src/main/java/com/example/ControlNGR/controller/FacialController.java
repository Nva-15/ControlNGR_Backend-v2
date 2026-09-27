package com.example.ControlNGR.controller;

import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.FacialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Registro del rostro para la marcacion con reconocimiento facial.
 * Los descriptores guardados nunca se devuelven al navegador: la comparacion se hace en el servidor.
 */
@RestController
@RequestMapping("/api/face")
public class FacialController {

    private final FacialService facialService;
    private final EmpleadoService empleadoService;
    private final UsuarioActual usuarioActual;

    public FacialController(FacialService facialService, EmpleadoService empleadoService, UsuarioActual usuarioActual) {
        this.facialService = facialService;
        this.empleadoService = empleadoService;
        this.usuarioActual = usuarioActual;
    }

    /** Cuerpo de registro: capturas del rostro y aceptacion del consentimiento. */
    public record RegistroRequest(List<double[]> descriptores, Boolean consentimiento) {}

    /** Estado del rostro del usuario autenticado (y si la marcacion facial es obligatoria). */
    @GetMapping("/mi-estado")
    public ResponseEntity<?> miEstado() {
        return ResponseEntity.ok(facialService.estado(usuarioActual.empleadoRequerido()));
    }

    /** El propio empleado registra su rostro (solo una vez). */
    @PostMapping("/registrar")
    public ResponseEntity<?> registrarPropio(@RequestBody RegistroRequest req) {
        Usuario usuario = usuarioActual.requerido();
        Empleado empleado = usuarioActual.empleadoRequerido();
        return ResponseEntity.ok(facialService.registrar(empleado, req.descriptores(),
                Boolean.TRUE.equals(req.consentimiento()), usuario, true));
    }

    /** Registro en persona por la jefatura o el admin (reemplaza el rostro anterior). */
    @PostMapping("/registrar/{empleadoId}")
    public ResponseEntity<?> registrarOtro(@PathVariable("empleadoId") Integer empleadoId, @RequestBody RegistroRequest req) {
        Usuario editor = usuarioActual.requerido();
        Empleado objetivo = empleadoAdministrable(editor, empleadoId);
        return ResponseEntity.ok(facialService.registrar(objetivo, req.descriptores(),
                Boolean.TRUE.equals(req.consentimiento()), editor, false));
    }

    /** Estado del rostro de un empleado a cargo. */
    @GetMapping("/estado/{empleadoId}")
    public ResponseEntity<?> estado(@PathVariable("empleadoId") Integer empleadoId) {
        return ResponseEntity.ok(facialService.estado(empleadoAdministrable(usuarioActual.requerido(), empleadoId)));
    }

    /** Ids de los empleados con rostro registrado (para la lista de empleados). */
    @GetMapping("/registrados")
    public ResponseEntity<?> registrados() {
        Usuario usuario = usuarioActual.requerido();
        if (!Roles.esAdmin(usuario.getRol()) && !Roles.esGestion(usuario.getRol())) {
            throw new AccesoDenegadoException("No tiene permisos para ver esta información");
        }
        return ResponseEntity.ok(facialService.empleadosRegistrados());
    }

    /** Restablece (borra) el rostro para que el empleado lo registre de nuevo. */
    @DeleteMapping("/{empleadoId}")
    public ResponseEntity<?> restablecer(@PathVariable("empleadoId") Integer empleadoId) {
        Empleado objetivo = empleadoAdministrable(usuarioActual.requerido(), empleadoId);
        facialService.eliminar(objetivo);
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Rostro restablecido. El colaborador deberá registrarlo de nuevo"));
    }

    /** Solo el admin o quien administra al empleado (y nunca sobre si mismo). */
    private Empleado empleadoAdministrable(Usuario editor, Integer empleadoId) {
        Empleado objetivo = empleadoService.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        boolean propio = editor.getEmpleado() != null && editor.getEmpleado().getId().equals(empleadoId);
        if (propio || !Roles.puedeAdministrar(editor.getRol(), objetivo.getRol())) {
            throw new AccesoDenegadoException("No tiene permisos sobre el rostro de este colaborador");
        }
        return objetivo;
    }
}
