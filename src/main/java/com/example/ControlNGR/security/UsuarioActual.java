package com.example.ControlNGR.security;

import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Acceso al usuario autenticado de la peticion actual. */
@Component
public class UsuarioActual {

    private final UsuarioRepository usuarioRepository;

    public UsuarioActual(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Optional<Usuario> obtener() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return Optional.empty();
        }
        return usuarioRepository.findByUsername(auth.getName());
    }

    public Usuario requerido() {
        return obtener().orElseThrow(() -> new AccesoDenegadoException("No autenticado"));
    }

    /** Empleado del usuario autenticado; falla si es el admin u otro usuario sin empleado. */
    public Empleado empleadoRequerido() {
        Empleado empleado = requerido().getEmpleado();
        if (empleado == null) {
            throw new AccesoDenegadoException("Esta accion solo esta disponible para el personal");
        }
        return empleado;
    }

    /**
     * Permite ver datos de un empleado solo a el mismo o a quien tiene personal a cargo
     * (director, gerente, jefe, supervisor, gestor).
     */
    public void validarAccesoAEmpleado(Integer empleadoId) {
        Usuario usuario = requerido();
        if (Roles.esGestion(usuario.getRol())) return;
        Empleado propio = usuario.getEmpleado();
        if (propio == null || !propio.getId().equals(empleadoId)) {
            throw new AccesoDenegadoException("Solo puede consultar su propia información");
        }
    }
}
