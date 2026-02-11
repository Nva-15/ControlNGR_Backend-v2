package com.example.ControlNGR.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Inyectamos la ruta desde application.properties
    @Value("${app.storage.location}")
    private String storageLocation;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Servir archivos estáticos del frontend
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600)
                .resourceChain(true);

        // Servir imágenes de empleados
        // Asegurarse de que la ruta termine con /
        String rutaImagen = storageLocation;
        if (!rutaImagen.endsWith("/")) {
            rutaImagen += "/";
        }

        registry.addResourceHandler("/img/**")
                .addResourceLocations(rutaImagen)
                .setCachePeriod(86400);
    }
}