package com.example.ControlNGR.config;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controlador para manejar las rutas del SPA (Single Page Application).
 * Redirige todas las rutas que no son API ni recursos estáticos a index.html
 * para que Angular Router pueda manejarlas.
 */
@Controller
public class SpaController implements ErrorController {

    /**
     * Redirige las rutas del frontend a index.html para que Angular las maneje.
     */
    @GetMapping(value = {
        "/",
        "/dashboard",
        "/login",
        "/asistencia",
        "/horarios",
        "/solicitudes",
        "/organigrama",
        "/empleados",
        "/reportes",
        "/eventos",
        "/eventos/{id}",
        "/eventos/{id}/estadisticas"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }

    /**
     * Maneja errores 404 redirigiendo a index.html para que Angular maneje la ruta.
     * Esto captura cualquier ruta no encontrada que debería ser manejada por Angular.
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        // Si es una petición de API, no redirigir
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return "forward:/error";
        }

        // Si es un archivo estático, no redirigir
        if (uri != null && (uri.endsWith(".js") || uri.endsWith(".css") ||
            uri.endsWith(".ico") || uri.endsWith(".png") || uri.endsWith(".jpg") ||
            uri.endsWith(".woff") || uri.endsWith(".woff2") || uri.endsWith(".ttf"))) {
            return "forward:/error";
        }

        // Para cualquier otra ruta, redirigir a index.html
        return "forward:/index.html";
    }
}
