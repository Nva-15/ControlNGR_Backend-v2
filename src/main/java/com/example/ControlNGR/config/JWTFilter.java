package com.example.ControlNGR.config;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.UsuarioRepository;
import com.example.ControlNGR.service.JWTUtil;
import com.example.ControlNGR.service.UsuarioService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Valida el token y carga el usuario desde la base de datos en cada peticion,
 * asi un cambio de rol o una desactivacion tienen efecto inmediato.
 * Si el usuario debe cambiar su contraseña, solo se le permite usar /api/auth/**.
 */
@Component
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public JWTFilter(JWTUtil jwtUtil, UsuarioRepository usuarioRepository, UsuarioService usuarioService) {
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String jwt = authHeader.substring(7);
            if (jwtUtil.validateToken(jwt)) {
                String username = jwtUtil.extractUsername(jwt);
                Optional<Usuario> usuarioOpt = username != null ? usuarioRepository.findByUsername(username) : Optional.empty();

                if (usuarioOpt.isPresent() && usuarioService.puedeIngresar(usuarioOpt.get())) {
                    Usuario usuario = usuarioOpt.get();

                    if (Boolean.TRUE.equals(usuario.getDebeCambiarPassword()) && !esRutaDeAutenticacion(request)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(
                            "{\"error\":\"Debe cambiar su contraseña antes de continuar\",\"debeCambiarPassword\":true}");
                        return;
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            usuario.getUsername(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().toUpperCase())));
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean esRutaDeAutenticacion(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/api/auth/");
    }
}
