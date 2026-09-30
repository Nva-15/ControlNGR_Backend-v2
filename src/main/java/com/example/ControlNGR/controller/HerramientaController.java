package com.example.ControlNGR.controller;

import com.example.ControlNGR.service.HerramientaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * "+ Herramientas". Ver: todo el personal y el admin (cada uno las de su rol).
 * Crear/editar/eliminar: jefaturas, supervisores, gestor y admin (ver WebSecurityConfig).
 */
@RestController
@RequestMapping("/api/herramientas")
public class HerramientaController {

    private final HerramientaService herramientaService;

    public HerramientaController(HerramientaService herramientaService) {
        this.herramientaService = herramientaService;
    }

    @GetMapping
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(herramientaService.listar());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(herramientaService.crear(body));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(herramientaService.actualizar(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable("id") Integer id) {
        herramientaService.eliminar(id);
        return ResponseEntity.ok(Map.of("mensaje", "Herramienta eliminada"));
    }
}
