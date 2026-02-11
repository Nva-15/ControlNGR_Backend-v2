package com.example.ControlNGR.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.ControlNGR.service.ImageService;
import com.example.ControlNGR.service.EmpleadoService;
import com.example.ControlNGR.service.JWTUtil;
import java.util.Map;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

@RestController
@RequestMapping("/api/imagenes")
public class ImageController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ImageController.class);
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private EmpleadoService empleadoService;
    
    @Autowired
    private JWTUtil jwtUtil;
    
    @PostMapping("/upload/{empleadoId}")
    public ResponseEntity<?> uploadImage(
            @PathVariable("empleadoId") Integer empleadoId,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            // Validar token y permisos
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "No autorizado", "success", false));
            }
            
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Token inválido", "success", false));
            }
            
            // Obtener empleado
            Optional<com.example.ControlNGR.entity.Empleado> empleadoOpt = empleadoService.findById(empleadoId);
            if (!empleadoOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Empleado no encontrado", "success", false));
            }
            
            com.example.ControlNGR.entity.Empleado empleado = empleadoOpt.get();
            
            // Verificar permisos: Solo admin, supervisor o el propio usuario
            String username = jwtUtil.extractUsername(token);
            String rol = jwtUtil.extractRol(token);
            
            Optional<com.example.ControlNGR.entity.Empleado> usuarioActualOpt = empleadoService.findByUsername(username);
            if (!usuarioActualOpt.isPresent()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Usuario no encontrado", "success", false));
            }
            
            com.example.ControlNGR.entity.Empleado usuarioActual = usuarioActualOpt.get();
            
            boolean puedeModificar = "admin".equals(rol) || 
                                     "supervisor".equals(rol) || 
                                     usuarioActual.getId().equals(empleadoId);
            
            if (!puedeModificar) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No tiene permisos para modificar este perfil", "success", false));
            }
            
            // Subir imagen
            String nombreArchivo = imageService.subirImagenPerfil(archivo, empleado.getNombre());
            
            // Eliminar imagen anterior si existe (excepto la predeterminada)
            String imagenAnterior = empleado.getFoto();
            if (imagenAnterior != null && !imagenAnterior.equals("img/perfil.png")) {
                try {
                    imageService.eliminarImagenAnterior(imagenAnterior);
                } catch (Exception e) {
                    logger.warn("Error eliminando imagen anterior: {}", e.getMessage());
                    // Continuar aunque falle la eliminación
                }
            }
            
            // Actualizar empleado con nueva imagen
            empleado.setFoto(nombreArchivo);
            empleadoService.save(empleado);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Imagen actualizada correctamente",
                "foto", nombreArchivo,
                "ruta", nombreArchivo,
                "empleadoId", empleadoId
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "success", false));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir la imagen: " + e.getMessage(), "success", false));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno: " + e.getMessage(), "success", false));
        }
    }
    
    @GetMapping("/verificar/{nombreArchivo}")
    public ResponseEntity<?> verificarImagen(@PathVariable("nombreArchivo") String nombreArchivo) {
        try {
            boolean existe = imageService.existeImagen(nombreArchivo);
            
            return ResponseEntity.ok(Map.of(
                "existe", existe,
                "nombreArchivo", nombreArchivo
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al verificar imagen: " + e.getMessage()));
        }
    }
}