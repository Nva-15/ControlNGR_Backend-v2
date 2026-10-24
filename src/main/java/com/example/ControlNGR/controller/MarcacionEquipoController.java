package com.example.ControlNGR.controller;

import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.ModoMarcacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Panel admin: modo de marcación de asistencia por equipo (FACIAL o BOTON). */
@RestController
@RequestMapping("/api/admin/marcacion-equipos")
public class MarcacionEquipoController {

    private final ModoMarcacionService service;
    private final UsuarioActual usuarioActual;

    public MarcacionEquipoController(ModoMarcacionService service, UsuarioActual usuarioActual) {
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
        Object modo = body.get("modo");
        return ResponseEntity.ok(service.guardar(departamentoId, modo == null ? null : String.valueOf(modo),
                usuarioActual.requerido().getUsername()));
    }
}
