package com.example.ControlNGR.controller;

import com.example.ControlNGR.dto.FeriadoLaboradoDTO;
import com.example.ControlNGR.dto.MovimientoSaldoDTO;
import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Panel maestro. Solo accesible con rol admin (ver WebSecurityConfig).
 * Para registrar empleados el admin usa POST /api/empleados.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ParametroService parametroService;
    private final SaldoService saldoService;
    private final FeriadoLaboradoService feriadoLaboradoService;
    private final VacacionesService vacacionesService;
    private final UsuarioActual usuarioActual;
    private final RedService redService;

    public AdminController(AdminService adminService, ParametroService parametroService, SaldoService saldoService,
                           FeriadoLaboradoService feriadoLaboradoService, VacacionesService vacacionesService,
                           UsuarioActual usuarioActual, RedService redService) {
        this.adminService = adminService;
        this.parametroService = parametroService;
        this.saldoService = saldoService;
        this.feriadoLaboradoService = feriadoLaboradoService;
        this.vacacionesService = vacacionesService;
        this.usuarioActual = usuarioActual;
        this.redService = redService;
    }

    // ---------- Parametros ----------
    @GetMapping("/parametros")
    public ResponseEntity<?> parametros() {
        return ResponseEntity.ok(parametroService.listar());
    }

    @PutMapping("/parametros/{clave}")
    public ResponseEntity<?> actualizarParametro(@PathVariable("clave") String clave, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(parametroService.actualizar(clave, body.get("valor")));
    }

    /** IP con la que el servidor ve al equipo del administrador (para configurar segmentos). */
    @GetMapping("/mi-ip")
    public ResponseEntity<?> miIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = redService.ipCliente(request);
        return ResponseEntity.ok(Map.of("ip", String.valueOf(ip), "dentroDeRed", redService.ipPermitida(ip)));
    }

    // ---------- Segmentos de red ----------
    @GetMapping("/segmentos-red")
    public ResponseEntity<?> segmentos() {
        return ResponseEntity.ok(adminService.segmentos());
    }

    @PostMapping("/segmentos-red")
    public ResponseEntity<?> crearSegmento(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.guardarSegmento(null, body));
    }

    @PutMapping("/segmentos-red/{id}")
    public ResponseEntity<?> actualizarSegmento(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.guardarSegmento(id, body));
    }

    @DeleteMapping("/segmentos-red/{id}")
    public ResponseEntity<?> eliminarSegmento(@PathVariable("id") Integer id) {
        adminService.eliminarSegmento(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Segmento eliminado"));
    }

    // ---------- Feriados ----------
    @GetMapping("/feriados")
    public ResponseEntity<?> feriados(@RequestParam(value = "anio", required = false) Integer anio) {
        return ResponseEntity.ok(adminService.feriados(anio));
    }

    @PostMapping("/feriados")
    public ResponseEntity<?> crearFeriado(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.guardarFeriado(null, body));
    }

    @PutMapping("/feriados/{id}")
    public ResponseEntity<?> actualizarFeriado(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.guardarFeriado(id, body));
    }

    @DeleteMapping("/feriados/{id}")
    public ResponseEntity<?> eliminarFeriado(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(Map.of("success", true, "message", adminService.eliminarFeriado(id)));
    }

    // ---------- Departamentos ----------
    @GetMapping("/departamentos")
    public ResponseEntity<?> departamentos() {
        return ResponseEntity.ok(adminService.departamentos());
    }

    @PostMapping("/departamentos")
    public ResponseEntity<?> crearDepartamento(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.guardarDepartamento(null, body));
    }

    @PutMapping("/departamentos/{id}")
    public ResponseEntity<?> actualizarDepartamento(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.guardarDepartamento(id, body));
    }

    // ---------- Roles y reglas de aprobacion ----------
    @GetMapping("/tipos-usuario")
    public ResponseEntity<?> tiposUsuario() {
        return ResponseEntity.ok(adminService.tiposUsuario());
    }

    @PutMapping("/tipos-usuario/{id}")
    public ResponseEntity<?> actualizarTipoUsuario(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.actualizarTipoUsuario(id, body));
    }

    @GetMapping("/reglas-aprobacion")
    public ResponseEntity<?> reglas() {
        return ResponseEntity.ok(adminService.reglasAprobacion());
    }

    @PostMapping("/reglas-aprobacion")
    public ResponseEntity<?> crearRegla(@RequestBody Map<String, Integer> body) {
        adminService.crearRegla(body.get("solicitanteId"), body.get("aprobadorId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.reglasAprobacion());
    }

    @DeleteMapping("/reglas-aprobacion/{id}")
    public ResponseEntity<?> eliminarRegla(@PathVariable("id") Integer id) {
        adminService.eliminarRegla(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Regla eliminada"));
    }

    // ---------- Tipos de solicitud y motivos de licencia ----------
    @GetMapping("/tipos-solicitud")
    public ResponseEntity<?> tiposSolicitud() {
        return ResponseEntity.ok(adminService.tiposSolicitud());
    }

    @PutMapping("/tipos-solicitud/{id}")
    public ResponseEntity<?> actualizarTipoSolicitud(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.actualizarTipoSolicitud(id, body));
    }

    @GetMapping("/motivos-licencia")
    public ResponseEntity<?> motivosLicencia() {
        return ResponseEntity.ok(adminService.motivosLicencia());
    }

    @PostMapping("/motivos-licencia")
    public ResponseEntity<?> crearMotivo(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.guardarMotivoLicencia(null, body));
    }

    @PutMapping("/motivos-licencia/{id}")
    public ResponseEntity<?> actualizarMotivo(@PathVariable("id") Integer id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.guardarMotivoLicencia(id, body));
    }

    // ---------- Usuarios ----------
    @GetMapping("/usuarios")
    public ResponseEntity<?> usuarios() {
        return ResponseEntity.ok(adminService.usuarios());
    }

    @PutMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable("id") Integer id, @RequestBody Map<String, String> body) {
        adminService.cambiarRol(id, body.get("rol"));
        return ResponseEntity.ok(Map.of("success", true, "message", "Rol actualizado"));
    }

    @PatchMapping("/usuarios/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable("id") Integer id, @RequestBody Map<String, Boolean> body) {
        adminService.cambiarEstadoUsuario(id, Boolean.TRUE.equals(body.get("activo")));
        return ResponseEntity.ok(Map.of("success", true, "message", "Estado actualizado"));
    }

    @PostMapping("/usuarios/{id}/restablecer-password")
    public ResponseEntity<?> restablecerPassword(@PathVariable("id") Integer id, @RequestBody Map<String, String> body) {
        adminService.restablecerPassword(id, body.get("passwordNueva"));
        return ResponseEntity.ok(Map.of("success", true,
                "message", "Contraseña restablecida. El usuario deberá cambiarla al ingresar."));
    }

    // ---------- Saldos ----------
    @GetMapping("/saldos")
    public ResponseEntity<?> saldos() {
        return ResponseEntity.ok(adminService.saldosTodos());
    }

    @GetMapping("/saldos/{empleadoId}/movimientos")
    public ResponseEntity<List<MovimientoSaldoDTO>> movimientos(@PathVariable("empleadoId") Integer empleadoId,
                                                                @RequestParam(value = "tipo", required = false) String tipo) {
        return ResponseEntity.ok(saldoService.movimientos(empleadoId, SaldoController.parseTipo(tipo))
                .stream().map(MovimientoSaldoDTO::de).toList());
    }

    /** Body: { empleadoId, vacaciones?, compensacion?, observacion? } */
    @PostMapping("/saldos/carga-inicial")
    public ResponseEntity<?> cargaInicial(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.cargaInicial(
                toInt(body.get("empleadoId")), toDecimal(body.get("vacaciones")),
                toDecimal(body.get("compensacion")), (String) body.get("observacion")));
    }

    /** Body: { empleadoId, tipoSaldo: VACACIONES|COMPENSACION, dias: (+/-), observacion } */
    @PostMapping("/saldos/ajuste")
    public ResponseEntity<?> ajuste(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(adminService.ajustarSaldo(
                toInt(body.get("empleadoId")), SaldoController.parseTipo((String) body.get("tipoSaldo")),
                toDecimal(body.get("dias")), (String) body.get("observacion")));
    }

    // ---------- Feriados laborados y vacaciones ----------
    @GetMapping("/feriados-laborados")
    public ResponseEntity<?> feriadosLaborados(@RequestParam(value = "empleadoId", required = false) Integer empleadoId) {
        return ResponseEntity.ok(feriadoLaboradoService.listar(empleadoId).stream().map(FeriadoLaboradoDTO::de).toList());
    }

    @PostMapping("/feriados-laborados/{id}/revertir")
    public ResponseEntity<?> revertirFeriadoLaborado(@PathVariable("id") Integer id, @RequestBody(required = false) Map<String, String> body) {
        String motivo = body != null ? body.get("motivo") : null;
        return ResponseEntity.ok(FeriadoLaboradoDTO.de(feriadoLaboradoService.revertir(id, motivo, usuarioActual.requerido())));
    }

    /** Ejecuta ahora el abono de aniversarios (normalmente corre solo cada dia a las 00:15). */
    @PostMapping("/vacaciones/procesar")
    public ResponseEntity<?> procesarVacaciones() {
        int abonados = vacacionesService.procesarAniversarios(LocalDate.now());
        return ResponseEntity.ok(Map.of("success", true, "periodosAbonados", abonados));
    }

    private static Integer toInt(Object v) {
        if (v == null) throw new IllegalArgumentException("empleadoId es requerido");
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numerico no valido: " + v);
        }
    }
}
