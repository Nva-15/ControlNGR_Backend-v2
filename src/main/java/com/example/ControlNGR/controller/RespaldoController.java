package com.example.ControlNGR.controller;

import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.RespaldoService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/** Respaldos de la base de datos. Solo admin (ruta /api/admin/**, ver WebSecurityConfig). */
@RestController
@RequestMapping("/api/admin/respaldos")
public class RespaldoController {

    private final RespaldoService respaldoService;
    private final UsuarioActual usuarioActual;

    public RespaldoController(RespaldoService respaldoService, UsuarioActual usuarioActual) {
        this.respaldoService = respaldoService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public ResponseEntity<?> estado() {
        return ResponseEntity.ok(respaldoService.estado());
    }

    @PutMapping("/programacion")
    public ResponseEntity<?> programar(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(respaldoService.actualizarProgramacion(body, usuarioActual.requerido().getUsername()));
    }

    /** Respaldo inmediato; se genera en segundo plano. */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody(required = false) Map<String, Object> body) {
        boolean incluirArchivos = body == null || !Boolean.FALSE.equals(body.get("incluirArchivos"));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(respaldoService.crearManual(incluirArchivos, usuarioActual.requerido().getUsername()));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<FileSystemResource> descargar(@PathVariable("id") Long id) {
        Path archivo = respaldoService.archivo(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.getFileName().toString()).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new FileSystemResource(archivo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable("id") Long id) {
        respaldoService.eliminar(id);
        return ResponseEntity.ok(Map.of("mensaje", "Respaldo eliminado"));
    }
}
