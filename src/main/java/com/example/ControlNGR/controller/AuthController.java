package com.example.ControlNGR.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.ControlNGR.dto.AuthRequest;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.JWTUtil;
import com.example.ControlNGR.service.EmailService;
import com.example.ControlNGR.service.UsuarioService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final UsuarioService usuarioService;
    private final EmpleadoService empleadoService;
    private final UsuarioActual usuarioActual;
    private final JWTUtil jwtUtil;
    private final EmailService emailService;

    public AuthController(UsuarioService usuarioService, EmpleadoService empleadoService,
                          UsuarioActual usuarioActual, JWTUtil jwtUtil, EmailService emailService) {
        this.usuarioService = usuarioService;
        this.empleadoService = empleadoService;
        this.usuarioActual = usuarioActual;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {
        Optional<Usuario> usuarioOpt = usuarioService.autenticar(authRequest.getUsername(), authRequest.getPassword());
        if (usuarioOpt.isEmpty()) {
            logger.warn("Credenciales invalidas para: {}", authRequest.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales inválidas o usuario inactivo"));
        }
        Usuario usuario = usuarioOpt.get();
        logger.info("Login exitoso: {} ({})", usuario.getUsername(), usuario.getRol());
        return ResponseEntity.ok(respuestaSesion(usuario));
    }

    /** Respuesta comun de login y cambio de contraseña (incluye un token nuevo). */
    private Map<String, Object> respuestaSesion(Usuario usuario) {
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtUtil.generateToken(usuario.getUsername(), usuario.getRol()));
        response.put("debeCambiarPassword", usuario.getDebeCambiarPassword());

        Map<String, Object> usuarioData = new HashMap<>();
        usuarioData.put("id", usuario.getId());
        usuarioData.put("username", usuario.getUsername());
        usuarioData.put("rol", usuario.getRol());
        usuarioData.put("rolNombre", usuario.getTipoUsuario().getNombre());
        usuarioData.put("esAdmin", Roles.esAdmin(usuario.getRol()));
        usuarioData.put("debeCambiarPassword", usuario.getDebeCambiarPassword());
        response.put("usuario", usuarioData);

        Empleado empleado = usuario.getEmpleado();
        if (empleado != null) {
            Map<String, Object> empleadoData = new HashMap<>();
            empleadoData.put("id", empleado.getId());
            empleadoData.put("dni", empleado.getDni());
            empleadoData.put("nombre", empleado.getNombre());
            empleadoData.put("cargo", empleado.getCargo());
            empleadoData.put("nivel", empleado.getNivel());
            empleadoData.put("rol", usuario.getRol());
            empleadoData.put("username", usuario.getUsername());
            empleadoData.put("email", empleado.getEmail());
            empleadoData.put("foto", empleado.getFoto());
            empleadoData.put("descripcion", empleado.getDescripcion());
            empleadoData.put("hobby", empleado.getHobby());
            empleadoData.put("cumpleanos", empleado.getCumpleanos());
            empleadoData.put("ingreso", empleado.getIngreso());
            empleadoData.put("departamentoId", empleado.getDepartamentoId());
            empleadoData.put("departamentoNombre", empleado.getDepartamentoNombre());
            response.put("empleado", empleadoData);
        } else {
            response.put("empleado", null);
        }
        return response;
    }

    // Obtener perfil completo del usuario autenticado
    @GetMapping("/perfil")
    public ResponseEntity<?> obtenerPerfil() {
        Optional<Usuario> usuarioOpt = usuarioActual.obtener();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No autorizado", "success", false));
        }
        Usuario usuario = usuarioOpt.get();
        if (usuario.getEmpleado() == null) {
            return ResponseEntity.ok(Map.of("success", true, "perfil", Map.of(
                    "username", usuario.getUsername(), "rol", usuario.getRol(), "esAdmin", true)));
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "perfil", empleadoService.obtenerPerfilCompleto(usuario.getEmpleado().getId())
        ));
    }

    // Actualizar información personal
    @PutMapping("/perfil/actualizar")
    public ResponseEntity<?> actualizarInformacionPersonal(@RequestBody Map<String, Object> datos) {
        try {
            Empleado empleado = usuarioActual.empleadoRequerido();
            return ResponseEntity.ok(empleadoService.actualizarInformacionPersonal(empleado.getId(), datos));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    /**
     * Cambiar contraseña (tambien se usa para el cambio obligatorio del primer ingreso).
     * Devuelve un token nuevo para continuar sin volver a iniciar sesion.
     */
    @PostMapping({"/cambiar-password", "/cambiar-password-perfil"})
    public ResponseEntity<?> cambiarPassword(@RequestBody Map<String, String> request) {
        Optional<Usuario> usuarioOpt = usuarioActual.obtener();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No autorizado", "success", false));
        }
        String passwordActual = request.get("passwordActual");
        String passwordNueva = request.get("passwordNueva");
        String confirmar = request.getOrDefault("confirmarPassword", passwordNueva);

        if (passwordNueva == null || !passwordNueva.equals(confirmar)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Las contraseñas no coinciden", "success", false));
        }
        try {
            Usuario usuario = usuarioService.cambiarPassword(usuarioOpt.get(), passwordActual, passwordNueva);
            Empleado empleado = usuario.getEmpleado();
            if (empleado != null && empleado.getEmail() != null) {
                try {
                    emailService.enviarNotificacionCambioPassword(empleado.getEmail(), empleado.getNombre());
                } catch (Exception e) {
                    logger.warn("Error enviando email de notificacion: {}", e.getMessage());
                }
            }
            Map<String, Object> respuesta = respuestaSesion(usuario);
            respuesta.put("success", true);
            respuesta.put("message", "Contraseña cambiada exitosamente");
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "success", false));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyToken() {
        Optional<Usuario> usuarioOpt = usuarioActual.obtener();
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("valid", false, "error", "Token inválido"));
        }
        Usuario usuario = usuarioOpt.get();
        return ResponseEntity.ok(Map.of(
            "valid", true,
            "username", usuario.getUsername(),
            "rol", usuario.getRol(),
            "debeCambiarPassword", usuario.getDebeCambiarPassword()
        ));
    }
}
