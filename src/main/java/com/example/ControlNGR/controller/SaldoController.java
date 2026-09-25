package com.example.ControlNGR.controller;

import com.example.ControlNGR.dto.FeriadoLaboradoDTO;
import com.example.ControlNGR.dto.MovimientoSaldoDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.TipoSaldo;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.FeriadoLaboradoService;
import com.example.ControlNGR.service.SaldoService;
import com.example.ControlNGR.service.SolicitudService;
import com.example.ControlNGR.service.VacacionesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consulta de saldos de vacaciones y dias por compensar. */
@RestController
@RequestMapping("/api/saldos")
public class SaldoController {

    private final SaldoService saldoService;
    private final VacacionesService vacacionesService;
    private final FeriadoLaboradoService feriadoLaboradoService;
    private final SolicitudService solicitudService;
    private final EmpleadoRepository empleadoRepository;
    private final UsuarioActual usuarioActual;

    public SaldoController(SaldoService saldoService, VacacionesService vacacionesService,
                           FeriadoLaboradoService feriadoLaboradoService, SolicitudService solicitudService,
                           EmpleadoRepository empleadoRepository, UsuarioActual usuarioActual) {
        this.saldoService = saldoService;
        this.vacacionesService = vacacionesService;
        this.feriadoLaboradoService = feriadoLaboradoService;
        this.solicitudService = solicitudService;
        this.empleadoRepository = empleadoRepository;
        this.usuarioActual = usuarioActual;
    }

    /** Saldos del usuario autenticado. */
    @GetMapping("/mi-saldo")
    public ResponseEntity<?> miSaldo() {
        return ResponseEntity.ok(detalle(usuarioActual.empleadoRequerido(), null));
    }

    /** Movimientos del usuario autenticado (tipo opcional: VACACIONES o COMPENSACION). */
    @GetMapping("/mis-movimientos")
    public ResponseEntity<List<MovimientoSaldoDTO>> misMovimientos(@RequestParam(value = "tipo", required = false) String tipo) {
        Empleado e = usuarioActual.empleadoRequerido();
        return ResponseEntity.ok(saldoService.movimientos(e.getId(), parseTipo(tipo)).stream().map(MovimientoSaldoDTO::de).toList());
    }

    /** Saldos de un empleado: el propio, sus aprobadores, quien lo administra, gerencia o admin. */
    @GetMapping("/empleado/{id}")
    public ResponseEntity<?> saldoEmpleado(@PathVariable("id") Integer id,
                                           @RequestParam(value = "tipo", required = false) String tipo) {
        Empleado objetivo = empleadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        validarPuedeVer(objetivo);
        return ResponseEntity.ok(detalle(objetivo, parseTipo(tipo)));
    }

    private Map<String, Object> detalle(Empleado e, TipoSaldo tipo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("empleadoId", e.getId());
        r.put("empleadoNombre", e.getNombre());
        r.put("saldos", saldoService.resumen(e.getId()));
        r.put("movimientos", saldoService.movimientos(e.getId(), tipo).stream().map(MovimientoSaldoDTO::de).toList());
        r.put("feriadosLaborados", feriadoLaboradoService.listar(e.getId()).stream().map(FeriadoLaboradoDTO::de).toList());
        r.put("periodosVacacionales", vacacionesService.periodos(e.getId()).stream().map(p -> Map.of(
                "id", p.getId(),
                "periodoInicio", p.getPeriodoInicio(),
                "periodoFin", p.getPeriodoFin(),
                "fechaAdquisicion", p.getFechaAdquisicion(),
                "diasGanados", p.getDiasGanados())).toList());
        return r;
    }

    private void validarPuedeVer(Empleado objetivo) {
        Usuario u = usuarioActual.requerido();
        if (Roles.esAdmin(u.getRol()) || Roles.esGerencia(u.getRol())) return;
        Empleado actual = u.getEmpleado();
        if (actual != null && (actual.getId().equals(objetivo.getId())
                || Roles.puedeAdministrar(u.getRol(), objetivo.getRol())
                || solicitudService.puedeAprobar(actual, objetivo))) return;
        throw new AccesoDenegadoException("No tiene permisos para ver los saldos de este empleado");
    }

    static TipoSaldo parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) return null;
        try {
            return TipoSaldo.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de saldo no valido. Use VACACIONES o COMPENSACION");
        }
    }
}
