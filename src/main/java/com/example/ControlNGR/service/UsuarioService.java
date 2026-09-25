package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.TipoUsuario;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.TipoUsuarioRepository;
import com.example.ControlNGR.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UsuarioService {

    public static final int PASSWORD_MIN = 8;

    private final UsuarioRepository usuarioRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                         TipoUsuarioRepository tipoUsuarioRepository,
                         PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.tipoUsuarioRepository = tipoUsuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Usuario valido para iniciar sesion: activo, con rol activo y, si es personal, empleado activo. */
    public boolean puedeIngresar(Usuario u) {
        if (u == null || !Boolean.TRUE.equals(u.getActivo())) return false;
        if (u.getTipoUsuario() == null || !Boolean.TRUE.equals(u.getTipoUsuario().getActivo())) return false;
        Empleado e = u.getEmpleado();
        return e == null ? Boolean.TRUE.equals(u.getTipoUsuario().getEsSistema()) : Boolean.TRUE.equals(e.getActivo());
    }

    @Transactional
    public Optional<Usuario> autenticar(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        Optional<Usuario> opt = usuarioRepository.findByUsername(username.trim());
        if (opt.isEmpty()) return Optional.empty();
        Usuario u = opt.get();
        if (!puedeIngresar(u) || !passwordEncoder.matches(password, u.getPassword())) {
            return Optional.empty();
        }
        u.setUltimoAcceso(LocalDateTime.now());
        return Optional.of(usuarioRepository.save(u));
    }

    @Transactional
    public Usuario cambiarPassword(Usuario usuario, String actual, String nueva) {
        if (actual == null || !passwordEncoder.matches(actual, usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }
        validarNueva(nueva);
        if (passwordEncoder.matches(nueva, usuario.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña no puede ser igual a la actual");
        }
        usuario.setPassword(passwordEncoder.encode(nueva));
        usuario.setDebeCambiarPassword(false);
        return usuarioRepository.save(usuario);
    }

    /** Restablecimiento por el admin: el usuario debera cambiarla al ingresar. */
    @Transactional
    public Usuario restablecerPassword(Usuario usuario, String nueva) {
        validarNueva(nueva);
        usuario.setPassword(passwordEncoder.encode(nueva));
        usuario.setDebeCambiarPassword(true);
        return usuarioRepository.save(usuario);
    }

    /** Crea el usuario de un empleado nuevo (username por defecto: DNI). */
    @Transactional
    public Usuario crearParaEmpleado(Empleado empleado, String username, String password, String rol) {
        String user = (username == null || username.isBlank()) ? empleado.getDni() : username.trim();
        if (usuarioRepository.existsByUsername(user)) {
            throw new IllegalArgumentException("El usuario '" + user + "' ya existe");
        }
        Usuario u = new Usuario();
        u.setEmpleado(empleado);
        u.setUsername(user);
        String clave = (password == null || password.isBlank()) ? empleado.getDni() : password;
        u.setPassword(passwordEncoder.encode(clave));
        u.setTipoUsuario(tipoPersonal(rol));
        u.setActivo(true);
        u.setDebeCambiarPassword(true);
        u = usuarioRepository.save(u);
        empleado.setUsuario(u);
        return u;
    }

    /** Rol de personal valido (no permite asignar admin a un empleado). */
    public TipoUsuario tipoPersonal(String rol) {
        String codigo = (rol == null || rol.isBlank()) ? "tecnico" : rol.trim().toLowerCase();
        TipoUsuario tipo = tipoUsuarioRepository.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Rol no valido: " + codigo));
        if (Boolean.TRUE.equals(tipo.getEsSistema())) {
            throw new IllegalArgumentException("El rol '" + codigo + "' no se puede asignar a un empleado");
        }
        return tipo;
    }

    private void validarNueva(String nueva) {
        if (nueva == null || nueva.length() < PASSWORD_MIN) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + PASSWORD_MIN + " caracteres");
        }
        if (!nueva.matches(".*[A-Za-z].*") || !nueva.matches(".*\\d.*")) {
            throw new IllegalArgumentException("La contraseña debe contener letras y numeros");
        }
    }
}
