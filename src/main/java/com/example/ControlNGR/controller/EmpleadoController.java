package com.example.ControlNGR.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.JWTUtil;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private JWTUtil jwtUtil;

    /** Obtiene todos los empleados. */
    @GetMapping
    public ResponseEntity<List<Empleado>> getAllEmpleados() {
        List<Empleado> empleados = empleadoService.findAll();
        return ResponseEntity.ok(empleados);
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

    /** Crea un nuevo empleado. */
    @PostMapping
    public ResponseEntity<?> createEmpleado(@RequestBody Empleado empleado) {
        try {
            if (empleadoService.existsByDni(empleado.getDni())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Ya existe un empleado con ese DNI"));
            }
            if (empleado.getPassword() == null || empleado.getPassword().trim().isEmpty()) {
                empleado.setPassword("password");
            }
            Empleado empleadoGuardado = empleadoService.save(empleado);
            return ResponseEntity.status(HttpStatus.CREATED).body(empleadoGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear empleado: " + e.getMessage()));
        }
    }

    /** Actualiza un empleado existente. */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmpleado(
            @PathVariable("id") Integer id,
            @RequestBody Empleado empleado,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;

            if (token == null) {
                if (!empleadoService.findById(id).isPresent()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Empleado no encontrado"));
                }
                empleado.setId(id);
                Empleado empleadoActualizado = empleadoService.save(empleado);
                return ResponseEntity.ok(empleadoActualizado);
            }

            String username = jwtUtil.extractUsername(token);
            Optional<Empleado> editorOpt = empleadoService.findByUsername(username);
            if (!editorOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Usuario no encontrado"));
            }

            Empleado editor = editorOpt.get();

            Optional<Empleado> empleadoObjetivoOpt = empleadoService.findById(id);
            if (!empleadoObjetivoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Empleado no encontrado"));
            }

            Empleado empleadoObjetivo = empleadoObjetivoOpt.get();

            if (!puedeEditarEmpleado(editor, empleadoObjetivo)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tiene permisos para editar este empleado"));
            }

            empleado.setId(id);
            Empleado empleadoActualizado = empleadoService.save(empleado);
            return ResponseEntity.ok(empleadoActualizado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar empleado: " + e.getMessage()));
        }
    }

    private boolean puedeEditarEmpleado(Empleado editor, Empleado objetivo) {
        String rolEditor = editor.getRol();
        String rolObjetivo = objetivo.getRol();
        boolean esMismoPerfil = editor.getId().equals(objetivo.getId());

        if ("admin".equals(rolEditor)) {
            return true;
        }

        if ("supervisor".equals(rolEditor)) {
            if (esMismoPerfil) {
                return true;
            }
            if ("tecnico".equals(rolObjetivo) || "hd".equals(rolObjetivo) || "noc".equals(rolObjetivo)) {
                return true;
            }
            return false;
        }

        return esMismoPerfil;
    }

    /** Elimina un empleado. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmpleado(
            @PathVariable("id") Integer id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;

            if (token == null) {
                Optional<Empleado> empleadoOpt = empleadoService.findById(id);
                if (!empleadoOpt.isPresent()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Empleado no encontrado"));
                }
                empleadoService.deleteById(id);
                return ResponseEntity.ok(Map.of("message", "Empleado eliminado permanentemente"));
            }

            String username = jwtUtil.extractUsername(token);
            Optional<Empleado> editorOpt = empleadoService.findByUsername(username);
            if (!editorOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Usuario no encontrado"));
            }

            Empleado editor = editorOpt.get();

            Optional<Empleado> empleadoOpt = empleadoService.findById(id);
            if (!empleadoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Empleado no encontrado"));
            }

            Empleado empleadoObjetivo = empleadoOpt.get();

            if (editor.getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No puede eliminarse a sí mismo"));
            }

            if (!puedeEliminarEmpleado(editor, empleadoObjetivo)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tiene permisos para eliminar este empleado"));
            }

            empleadoService.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Empleado eliminado permanentemente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al eliminar empleado: " + e.getMessage()));
        }
    }

    private boolean puedeEliminarEmpleado(Empleado editor, Empleado objetivo) {
        String rolEditor = editor.getRol();
        String rolObjetivo = objetivo.getRol();

        if (editor.getId().equals(objetivo.getId())) {
            return false;
        }

        if ("admin".equals(rolEditor)) {
            return true;
        }

        if ("supervisor".equals(rolEditor)) {
            if ("tecnico".equals(rolObjetivo) || "hd".equals(rolObjetivo) || "noc".equals(rolObjetivo)) {
                return true;
            }
            return false;
        }

        return false;
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
        List<Empleado> empleados = empleadoService.findByRol(rol);
        return ResponseEntity.ok(empleados);
    }

    /** Obtiene el perfil del usuario autenticado. */
    @GetMapping("/mi-perfil")
    public ResponseEntity<?> getMiPerfil(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String username = jwtUtil.extractUsername(token);
                Optional<Empleado> empleadoOpt = empleadoService.findByUsername(username);
                if (empleadoOpt.isPresent()) {
                    return ResponseEntity.ok(empleadoOpt.get());
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No autorizado"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener perfil"));
        }
    }

    /** Actualiza el perfil de un empleado. */
    @PutMapping("/actualizar-perfil/{id}")
    public ResponseEntity<?> actualizarPerfil(@PathVariable("id") Integer id, @RequestBody Map<String, Object> datos) {
        try {
            boolean actualizado = empleadoService.actualizarPerfil(id, datos);
            if (actualizado) {
                return ResponseEntity.ok(Map.of(
                    "message", "Perfil actualizado correctamente",
                    "success", true
                ));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar perfil: " + e.getMessage(), "success", false));
        }
    }

    /** Cambia la contraseña como administrador. */
    @PostMapping("/cambiar-password-admin/{id}")
    public ResponseEntity<?> cambiarPasswordAdmin(@PathVariable("id") Integer id, @RequestBody Map<String, String> request) {
        try {
            String passwordNueva = request.get("passwordNueva");
            if (passwordNueva == null || passwordNueva.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La nueva contraseña es requerida", "success", false));
            }

            if (passwordNueva.length() < 6) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La contraseña debe tener al menos 6 caracteres", "success", false));
            }

            boolean cambiado = empleadoService.cambiarPasswordAdmin(id, passwordNueva);
            if (cambiado) {
                return ResponseEntity.ok(Map.of(
                    "message", "Contraseña cambiada exitosamente",
                    "success", true
                ));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al cambiar contraseña: " + e.getMessage(), "success", false));
        }
    }

    /** Actualiza el email de un empleado. */
    @PutMapping("/actualizar-email/{id}")
    public ResponseEntity<?> actualizarEmail(@PathVariable("id") Integer id, @RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El email es requerido", "success", false));
            }

            if (!email.contains("@") || !email.contains(".")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Formato de email inválido", "success", false));
            }

            Optional<Empleado> empleadoExistente = empleadoService.findFirstByEmail(email);
            if (empleadoExistente.isPresent() && !empleadoExistente.get().getId().equals(id)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "El email ya está registrado por otro usuario", "success", false));
            }

            Map<String, Object> datos = new java.util.HashMap<>();
            datos.put("email", email);

            boolean actualizado = empleadoService.actualizarPerfil(id, datos);
            if (actualizado) {
                return ResponseEntity.ok(Map.of(
                    "message", "Email actualizado correctamente",
                    "success", true
                ));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Empleado no encontrado", "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar email: " + e.getMessage(), "success", false));
        }
    }

    /** Exporta todos los empleados. */
    @GetMapping("/exportar")
    public ResponseEntity<?> exportarEmpleados() {
        try {
            List<Map<String, Object>> datos = empleadoService.exportarEmpleados();
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                        "titulo", "Reporte de Empleados",
                        "fecha_generacion", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        "total_registros", datos.size(),
                        "datos", datos
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al exportar empleados: " + e.getMessage()));
        }
    }

    /** Cambia el estado de un empleado. */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstadoUsuario(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, Boolean> estado,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;

            if (token == null) {
                Optional<Empleado> empleadoOpt = empleadoService.findById(id);
                if (!empleadoOpt.isPresent()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Empleado no encontrado", "success", false));
                }
                Empleado empleado = empleadoOpt.get();
                if (estado.containsKey("usuarioActivo")) {
                    empleado.setUsuarioActivo(estado.get("usuarioActivo"));
                }
                if (estado.containsKey("activo")) {
                    empleado.setActivo(estado.get("activo"));
                }
                empleadoService.save(empleado);
                return ResponseEntity.ok(Map.of(
                    "message", "Estado actualizado correctamente",
                    "success", true,
                    "usuarioActivo", empleado.getUsuarioActivo(),
                    "activo", empleado.getActivo()
                ));
            }

            String username = jwtUtil.extractUsername(token);
            Optional<Empleado> editorOpt = empleadoService.findByUsername(username);
            if (!editorOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Usuario no encontrado", "success", false));
            }

            Empleado editor = editorOpt.get();

            Optional<Empleado> empleadoOpt = empleadoService.findById(id);
            if (!empleadoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Empleado no encontrado", "success", false));
            }

            Empleado empleadoObjetivo = empleadoOpt.get();

            if (editor.getId().equals(id)) {
                Boolean intentaDesactivar = estado.getOrDefault("usuarioActivo", true) == false ||
                                            estado.getOrDefault("activo", true) == false;
                if (intentaDesactivar) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "No puede desactivarse a sí mismo", "success", false));
                }
            }

            if (!puedeModificarEstado(editor, empleadoObjetivo)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tiene permisos para modificar el estado de este empleado", "success", false));
            }

            Empleado empleado = empleadoObjetivo;

            if (estado.containsKey("usuarioActivo")) {
                empleado.setUsuarioActivo(estado.get("usuarioActivo"));
            }
            if (estado.containsKey("activo")) {
                empleado.setActivo(estado.get("activo"));
            }

            empleadoService.save(empleado);

            return ResponseEntity.ok(Map.of(
                "message", "Estado actualizado correctamente",
                "success", true,
                "usuarioActivo", empleado.getUsuarioActivo(),
                "activo", empleado.getActivo()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar estado: " + e.getMessage(), "success", false));
        }
    }

    private boolean puedeModificarEstado(Empleado editor, Empleado objetivo) {
        String rolEditor = editor.getRol();
        String rolObjetivo = objetivo.getRol();

        if ("admin".equals(rolEditor)) {
            return true;
        }

        if ("supervisor".equals(rolEditor)) {
            if ("tecnico".equals(rolObjetivo) || "hd".equals(rolObjetivo) || "noc".equals(rolObjetivo)) {
                return true;
            }
            return false;
        }

        return false;
    }

    /** Verifica los permisos del usuario sobre otro empleado. */
    @GetMapping("/permisos/{idObjetivo}")
    public ResponseEntity<?> verificarPermisos(
            @PathVariable("idObjetivo") Integer idObjetivo,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
            if (token == null) {
                return ResponseEntity.ok(Map.of(
                    "puedeEditar", true,
                    "puedeEliminar", true,
                    "puedeDesactivar", true,
                    "esMismoPerfil", false,
                    "rolEditor", "unknown",
                    "rolObjetivo", "unknown"
                ));
            }

            String username = jwtUtil.extractUsername(token);
            Optional<Empleado> editorOpt = empleadoService.findByUsername(username);
            if (!editorOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Usuario no encontrado"));
            }

            Empleado editor = editorOpt.get();

            Optional<Empleado> objetivoOpt = empleadoService.findById(idObjetivo);
            if (!objetivoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Empleado objetivo no encontrado"));
            }

            Empleado objetivo = objetivoOpt.get();
            boolean esMismoPerfil = editor.getId().equals(objetivo.getId());

            return ResponseEntity.ok(Map.of(
                "puedeEditar", puedeEditarEmpleado(editor, objetivo),
                "puedeEliminar", !esMismoPerfil && puedeEliminarEmpleado(editor, objetivo),
                "puedeDesactivar", !esMismoPerfil && puedeModificarEstado(editor, objetivo),
                "esMismoPerfil", esMismoPerfil,
                "rolEditor", editor.getRol(),
                "rolObjetivo", objetivo.getRol()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al verificar permisos: " + e.getMessage()));
        }
    }
}
