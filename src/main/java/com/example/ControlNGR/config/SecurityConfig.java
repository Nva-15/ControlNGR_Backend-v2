package com.example.ControlNGR.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {
    
    @Bean
    public HttpFirewall looseHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        
        // Permitir todos los caracteres problemáticos
        firewall.setAllowUrlEncodedSlash(true);
        firewall.setAllowUrlEncodedDoubleSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        firewall.setAllowSemicolon(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedPeriod(true);
        
        // Deshabilitar todas las validaciones estrictas
        firewall.setUnsafeAllowAnyHttpMethod(true);
        
        return firewall;
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Configurar recursos estáticos generales
        // NOTA: /img/** se configura en WebConfig con la ruta de almacenamiento externa
        registry.addResourceHandler("/**")
                .addResourceLocations(
                    "classpath:/static/",
                    "classpath:/public/",
                    "classpath:/resources/",
                    "classpath:/META-INF/resources/"
                )
                .setCachePeriod(0);
    }
}