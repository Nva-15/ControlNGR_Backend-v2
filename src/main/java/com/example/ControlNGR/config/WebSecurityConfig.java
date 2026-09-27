package com.example.ControlNGR.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import com.example.ControlNGR.security.Roles;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    private JWTFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // IMPORTANTE: Permitir /error para ver excepciones reales (400, 500) en vez de 403
                .requestMatchers("/error").permitAll()
                
                // Recursos estáticos y rutas de Angular
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login",
                    "/dashboard",
                    "/empleados",
                    "/asistencias",
                    "/asistencia",
                    "/solicitudes",
                    "/horarios",
                    "/organigrama",
                    "/reportes",
                    "/eventos",
                    "/eventos/**",
                    "/admin",
                    "/admin/**",
                    "/cambiar-password",
                    "/img/**",
                    "/css/**",
                    "/js/**",
                    "/static/**",
                    "/resources/**",
                    "/uploads/**",
                    "/assets/**",
                    "/media/**",
                    "/favicon.ico",
                    "/*.png",
                    "/*.jpg",
                    "/*.jpeg",
                    "/*.gif",
                    "/*.svg",
                    "/*.ico",
                    "/*.html",
                    "/*.js",           // Archivos JavaScript de Angular
                    "/*.css",          // Archivos CSS de Angular
                    "/*.map",          // Source maps
                    "/*.woff",         // Fuentes
                    "/*.woff2",        // Fuentes
                    "/*.ttf",          // Fuentes
                    "/*.eot",          // Fuentes
                    "/chunk-*.js",     // Chunks de Angular
                    "/main-*.js",      // Main bundle de Angular
                    "/polyfills-*.js", // Polyfills de Angular
                    "/scripts-*.js",   // Scripts de Angular
                    "/styles-*.css",   // Estilos de Angular
                    "/webjars/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                
                // Endpoints públicos de API (login). El resto de /api/auth valida el usuario en el controlador
                .requestMatchers("/api/auth/**").permitAll()

                // PANEL MAESTRO - solo admin
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // EMPLEADOS - ver: todos; crear/editar/eliminar: jefaturas, supervisores, gestor y admin
                // (el controlador valida sobre que empleados puede actuar cada rol)
                .requestMatchers(HttpMethod.GET, "/api/empleados/**").hasAnyRole(Roles.AUTH_PERSONAL_Y_ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/empleados/actualizar-perfil/**", "/api/empleados/actualizar-email/**")
                    .hasAnyRole(Roles.AUTH_PERSONAL_Y_ADMIN)
                .requestMatchers("/api/empleados/**").hasAnyRole(Roles.AUTH_GESTION_Y_ADMIN)

                // HORARIOS - ver: todos; modificar: jefaturas, supervisores, gestor y admin
                .requestMatchers("/api/horarios-semanales/test").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/horarios/**", "/api/horarios-semanales/**")
                    .hasAnyRole(Roles.AUTH_PERSONAL_Y_ADMIN)
                .requestMatchers("/api/horarios/**", "/api/horarios-semanales/**").hasAnyRole(Roles.AUTH_GESTION_Y_ADMIN)

                // Imagenes de perfil
                .requestMatchers("/api/imagenes/**").hasAnyRole(Roles.AUTH_PERSONAL_Y_ADMIN)

                // Listados y reportes de todo el personal: solo quienes tienen personal a cargo
                .requestMatchers(HttpMethod.GET, "/api/asistencia", "/api/asistencia/fecha/**", "/api/asistencia/rango",
                        "/api/asistencia/hoy", "/api/asistencia/reporte/rango",
                        "/api/solicitudes/todas", "/api/solicitudes/pendientes", "/api/solicitudes/historial",
                        "/api/solicitudes/exportar/**")
                    .hasAnyRole(Roles.AUTH_GESTION)
                .requestMatchers(HttpMethod.POST, "/api/asistencia/verificar-salidas").hasAnyRole(Roles.AUTH_GESTION)

                // Asistencia, solicitudes, saldos y notificaciones: solo personal (el admin no marca ni solicita)
                .requestMatchers("/api/asistencia/**", "/api/solicitudes/**", "/api/saldos/**", "/api/notificaciones/**")
                    .hasAnyRole(Roles.AUTH_PERSONAL)

                // EVENTOS - ver, responder y comentar: todo el personal; crear/editar/eliminar: gestion
                .requestMatchers(HttpMethod.GET, "/api/eventos/**").hasAnyRole(Roles.AUTH_PERSONAL)
                .requestMatchers(HttpMethod.POST, "/api/eventos/responder", "/api/eventos/*/comentarios")
                    .hasAnyRole(Roles.AUTH_PERSONAL)
                .requestMatchers("/api/eventos/**").hasAnyRole(Roles.AUTH_GESTION)

                // El resto requiere autenticación
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                // Respuestas JSON claras en lugar de redirigir a la pagina de error
                .authenticationEntryPoint((request, response, e) ->
                    escribirError(response, 401, "No autenticado. Inicie sesión nuevamente."))
                .accessDeniedHandler((request, response, e) ->
                    escribirError(response, 403, "No tiene permisos para realizar esta acción")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escribirError(jakarta.servlet.http.HttpServletResponse response, int status, String mensaje)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\",\"success\":false}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permitir acceso desde localhost (desarrollo) y desde cualquier IP en red local (producción)
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*.*:*",
            "http://10.*.*.*:*",
            "http://172.16.*.*:*"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*")); // Permitir todos los headers
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}