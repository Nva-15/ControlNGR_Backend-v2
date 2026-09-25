package com.example.ControlNGR.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.dto.EmpleadoRequestDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.EmpleadoService;

/**
 * Gestion de empleados. Permisos:
 * admin y gerencia (director/gerente/jefe) sobre todos; supervisor sobre tecnico/hd/noc/bo;
 * gestor sobre asistentes; cada uno sobre su propio perfil (solo edicion).
 * Solo admin y gerencia pueden crear empleados y cambiar rol, usuario o contraseña.
 */
@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

    private final EmpleadoService empleadoService;
    private final UsuarioActual usuarioActual;

    public EmpleadoController(EmpleadoService empleadoService, UsuarioActual usuarioActual) {
        this.empleadoService = empleadoService;
        this.usuarioActual = usuarioActual;
    }

    /** Obtiene todos los empleados. */
    @GetMapping
    public ResponseEntity<List<Empleado>> getAllEmpleados() {
        return ResponseEntity.ok(empleadoService.findAll());
    }

    /** Obtiene un empleado por ID. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEmpleadoById(@PathVariable("id") Integer id) {
        Optional<Empleado> empleado = empleadoService.findById(id);
        if (empleado.isPresent()) {
            return ResponseEntity.ok(empleado.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Empleado no encontrado"));
    }

    /** Crea un nuevo empleado con su usuario (usuario y contraseña inicial: DNI). */
    @PostMapping
    public ResponseEntity<?> createEmpleado(@RequestBody EmpleadoRequestDTO datos) {
        Usuario editor = usuarioActual.requerido();
        if (!puedeGestionarAcceso(editor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo el administrador o gerencia pueden registrar empleados"));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(empleadoService.crearEmpleado(datos));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Actualiza un empleado existente. */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmpleado(@PathVariable("id") Integer id, @RequestBody EmpleadoRequestDTO datos) {
        Usuario editor = usuarioActual.requerido();
        Optional<Empleado> objetivoOpt = empleadoService.findById(id);
        if (objetivoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Empleado no encontrado"));
        }
        if (!puedeEditarEmpleado(editor, objetivoOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permisos para editar este empleado"));
        }
        try {
            return ResponseEntity.ok(empleadoService.actualizarEmpleado(id, datos, puedeGestionarAcceso(editor)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean esMismoPerfil(Usuario editor, Empleado objetivo) {
        return editor.getEmpleado() != null && editor.getEmpleado().getId().equals(objetivo.getId());
    }

    private boolean puedeGestionarAcceso(Usuario editor) {
        return Roles.esAdmin(editor.getRol()) || Roles.esGerencia(editor.getRol());
    }

    private boolean puedeEditarEmpleado(Usuario editor, Empleado objetivo) {
        return esMismoPerfil(editor, objetivo) || Roles.puedeAdministrar(editor.getRol(), objetivo.getRol());
    }

    private boolean puedeEliminarEmpleado(Usuario editor, Empleado objetivo) {
        return !esMismoPerfil(editor, objetivo) && Roles.puedeAdministrar(editor.getRol(), objetivo.getRol());
    }

    private boolean puedeModificarEstado(Usuario editor, Empleado objetivo) {
        return !esMismoPerfil(editor, objetivo) && Roles.puedeAdministrar(editor.getRol(), objetivo.getRol());
    }

    /** Elimina un empleado. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmpleado(@PathVariable("id") Integer id) {
        Usuario editor = usuarioActual.requerido();
        Optional<Empleado> empleadoOpt = empleadoService.findById(id);
        if (empleadoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Empleado no encontrado"));
        }
        if (esMismoPerfil(editor, empleadoOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No puede eliminarse a sí mismo"));
        }
        if (!puedeEliminarEmpleado(editor, empleadoOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permisos para eliminar este empleado"));
        }
        try {
            empleadoService.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Empleado eliminado permanentemente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                    "No se puede eliminar: el empleado tiene registros asociados. Desactívelo en su lugar."));
        }
    }

    /** Busca empleados por nombre. */
    @GetMapping("/buscar")
    public ResponseEntity<List<Empleado>> buscarEmpleados(@RequestParam("nombre") String nombre) {
        List<Empleado> empleados = empleadoService.findAll().stream()
                .filter(e -> e.getNombre().toLowerCase().contains(nombre.toLowerCase()))
                .toList();
        return ResponseEntity.ok(empleados);
    }

    /** Obtiene empleados por rol. */
    @GetMapping("/rol/{rol}")
    public ResponseEntity<List<Empleado>> getEmpleadosByRol(@PathVariable("rol") String rol) {
        return ResponseEntity.ok(empleadoService.findByRol(rol));
    }

    /** Obtiene el perfil del usuario autenticado. */
    @GetMapping("/mi-perfil")
    public ResponseEntity<?> getMiPerfil() {
        Optional<Usuario> usuario = usuarioActual.obtener();
        if (usuario.isPresent() && usuario.get().getEmpleado() != null) {
            return ResponseEntity.ok(usuario.get().getEmpleado());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No autorizado"));
    }

    /** Actualiza el perfil de un empleado. */
    @PutMapping("/actualizar-perfil/{id}")
    public ResponseEntity<?> actualizarPerfil(@PathVariable("id") Integer id, @RequestBody Map<String, Object> datos) {
        Usuario editor = usuarioActual.requerido();
        Optional<Empleado> objetivo = empleadoService.findById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        }
        if (!puedeEditarEmpleado(editor, objetivo.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permisos para editar este empleado", "success", false));
        }
        try {
            empleadoService.actualizarPerfil(id, datos);
            return ResponseEntity.ok(Map.of("message", "Perfil actualizado correctamente", "success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /** Restablece la contraseña (admin o gerencia). El empleado debera cambiarla al ingresar. */
    @PostMapping("/cambiar-password-admin/{id}")
    public ResponseEntity<?> cambiarPasswordAdmin(@PathVariable("id") Integer id, @RequestBody Map<String, String> request) {
        Usuario editor = usuarioActual.requerido();
        if (!puedeGestionarAcceso(editor)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permisos para restablecer contraseñas", "success", false));
        }
        try {
            boolean cambiado = empleadoService.cambiarPasswordAdmin(id, request.get("passwordNueva"));
            if (cambiado) {
                return ResponseEntity.ok(Map.of("message", "Contraseña restablecida. Deberá cambiarla al ingresar.",
                        "success", true));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /** Actualiza el email de un empleado. */
    @PutMapping("/actualizar-email/{id}")
    public ResponseEntity<?> actualizarEmail(@PathVariable("id") Integer id, @RequestBody Map<String, String> request) {
        Usuario editor = usuarioActual.requerido();
        Optional<Empleado> objetivo = empleadoService.findById(id);
        if (objetivo.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        }
        if (!puedeEditarEmpleado(editor, objetivo.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permisos para editar este empleado", "success", false));
        }
        String email = request.get("email");
        if (email == null || !email.contains("@") || !email.contains(".")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato de email inválido", "success", false));
        }
        try {
            empleadoService.actualizarPerfil(id, Map.of("email", email));
            return ResponseEntity.ok(Map.of("message", "Email actualizado correctamente", "success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /** Exporta todos los empleados. */
    @GetMapping("/exportar")
    public ResponseEntity<?> exportarEmpleados() {
        List<Map<String, Object>> datos = empleadoService.exportarEmpleados();
        return ResponseEntity.ok(Map.of(
                "titulo", "Reporte de Empleados",
                "fecha_generacion", java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                "total_registros", datos.size(),
                "datos", datos));
    }

    /** Cambia el estado de un empleado y/o su usuario. */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstadoUsuario(@PathVariable("id") Integer id, @RequestBody Map<String, Boolean> estado) {
        Usuario editor = usuarioActual.requerido();
        Optional<Empleado> empleadoOpt = empleadoService.findById(id);
        if (empleadoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        }
        Empleado objetivo = empleadoOpt.get();
        if (esMismoPerfil(editor, objetivo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No puede cambiar su propio estado", "success", false));
        }
        if (!puedeModificarEstado(editor, objetivo)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permisos para modificar el estado de este empleado", "success", false));
        }
        Empleado empleado = empleadoService.cambiarEstado(objetivo, estado.get("activo"), estado.get("usuarioActivo"));
        return ResponseEntity.ok(Map.of(
                "message", "Estado actualizado correctamente",
                "success", true,
                "usuarioActivo", empleado.getUsuarioActivo(),
                "activo", empleado.getActivo()));
    }

    /** Verifica los permisos del usuario sobre otro empleado. */
    @GetMapping("/permisos/{idObjetivo}")
    public ResponseEntity<?> verificarPermisos(@PathVariable("idObjetivo") Integer idObjetivo) {
        Usuario editor = usuarioActual.requerido();
        Optional<Empleado> objetivoOpt = empleadoService.findById(idObjetivo);
        if (objetivoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Empleado objetivo no encontrado"));
        }
        Empleado objetivo = objetivoOpt.get();
        return ResponseEntity.ok(Map.of(
                "puedeEditar", puedeEditarEmpleado(editor, objetivo),
                "puedeEliminar", puedeEliminarEmpleado(editor, objetivo),
                "puedeDesactivar", puedeModificarEstado(editor, objetivo),
                "puedeGestionarAcceso", puedeGestionarAcceso(editor),
                "esMismoPerfil", esMismoPerfil(editor, objetivo),
                "rolEditor", editor.getRol(),
                "rolObjetivo", String.valueOf(objetivo.getRol())));
    }
}
