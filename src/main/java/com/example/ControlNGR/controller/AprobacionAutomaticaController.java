package com.example.ControlNGR.controller;

import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.AprobacionAutomaticaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Panel admin: reglas de aprobación automática de compensaciones por equipo. */
@RestController
@RequestMapping("/api/admin/aprobacion-automatica")
public class AprobacionAutomaticaController {

    private final AprobacionAutomaticaService service;
    private final UsuarioActual usuarioActual;

    public AprobacionAutomaticaController(AprobacionAutomaticaService service, UsuarioActual usuarioActual) {
        this.service = service;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PutMapping("/{departamentoId}")
    public ResponseEntity<Map<String, Object>> guardar(@PathVariable("departamentoId") Integer departamentoId,
                                                       @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.guardar(departamentoId, body, usuarioActual.requerido().getUsername()));
    }
}
