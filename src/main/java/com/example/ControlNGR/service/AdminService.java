package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.SaldoResumenDTO;
import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.entity.MovimientoSaldo.Origen;
import com.example.ControlNGR.entity.MovimientoSaldo.TipoMovimiento;
import com.example.ControlNGR.repository.*;
import com.example.ControlNGR.security.IpMatcher;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Operaciones del panel maestro (solo rol admin). */
@Service
public class AdminService {

    private final SegmentoRedRepository segmentoRepository;
    private final DiaFeriadoRepository feriadoRepository;
    private final DepartamentoRepository departamentoRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final ReglaAprobacionRepository reglaRepository;
    private final TipoSolicitudRepository tipoSolicitudRepository;
    private final MotivoLicenciaRepository motivoLicenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpleadoRepository empleadoRepository;
    private final SaldoService saldoService;
    private final UsuarioService usuarioService;
    private final UsuarioActual usuarioActual;

    public AdminService(SegmentoRedRepository segmentoRepository, DiaFeriadoRepository feriadoRepository,
                        DepartamentoRepository departamentoRepository, TipoUsuarioRepository tipoUsuarioRepository,
                        ReglaAprobacionRepository reglaRepository, TipoSolicitudRepository tipoSolicitudRepository,
                        MotivoLicenciaRepository motivoLicenciaRepository, UsuarioRepository usuarioRepository,
                        EmpleadoRepository empleadoRepository, SaldoService saldoService,
                        UsuarioService usuarioService, UsuarioActual usuarioActual) {
        this.segmentoRepository = segmentoRepository;
        this.feriadoRepository = feriadoRepository;
        this.departamentoRepository = departamentoRepository;
        this.tipoUsuarioRepository = tipoUsuarioRepository;
        this.reglaRepository = reglaRepository;
        this.tipoSolicitudRepository = tipoSolicitudRepository;
        this.motivoLicenciaRepository = motivoLicenciaRepository;
        this.usuarioRepository = usuarioRepository;
        this.empleadoRepository = empleadoRepository;
        this.saldoService = saldoService;
        this.usuarioService = usuarioService;
        this.usuarioActual = usuarioActual;
    }

    // ==================== SEGMENTOS DE RED ====================

    @Transactional(readOnly = true)
    public List<SegmentoRed> segmentos() {
        return segmentoRepository.findAll();
    }

    @Transactional
    public SegmentoRed guardarSegmento(Integer id, Map<String, Object> d) {
        SegmentoRed s = id == null ? new SegmentoRed()
                : segmentoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Segmento no encontrado"));
        String patron = texto(d, "patron");
        if (patron != null) {
            patron = patron.trim();
            if (!IpMatcher.patronValido(patron)) {
                throw new IllegalArgumentException("Patron no valido. Use por ejemplo 10.92.104.% , 10.92.104.0/24 o una IP exacta");
            }
            if (!patron.equals(s.getPatron()) && segmentoRepository.existsByPatron(patron)) {
                throw new IllegalArgumentException("El segmento " + patron + " ya esta registrado");
            }
            s.setPatron(patron);
        }
        if (texto(d, "nombre") != null) s.setNombre(texto(d, "nombre").trim());
        if (d.containsKey("descripcion")) s.setDescripcion(texto(d, "descripcion"));
        if (d.get("activo") != null) s.setActivo(bool(d, "activo"));
        requerido(s.getNombre(), "El nombre es requerido");
        requerido(s.getPatron(), "El patron es requerido");
        return segmentoRepository.save(s);
    }

    @Transactional
    public void eliminarSegmento(Integer id) {
        segmentoRepository.deleteById(id);
    }

    // ==================== FERIADOS ====================

    @Transactional(readOnly = true)
    public List<DiaFeriado> feriados(Integer anio) {
        return anio != null ? feriadoRepository.findByAnio(anio) : feriadoRepository.findAllByOrderByFechaAsc();
    }

    @Transactional
    public DiaFeriado guardarFeriado(Integer id, Map<String, Object> d) {
        DiaFeriado f = id == null ? new DiaFeriado()
                : feriadoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Feriado no encontrado"));
        if (texto(d, "fecha") != null) {
            LocalDate fecha = LocalDate.parse(texto(d, "fecha"));
            if (!fecha.equals(f.getFecha()) && feriadoRepository.existsByFecha(fecha)) {
                throw new IllegalArgumentException("Ya existe un feriado el " + fecha);
            }
            f.setFecha(fecha);
        }
        if (texto(d, "descripcion") != null) f.setDescripcion(texto(d, "descripcion").trim());
        if (texto(d, "tipo") != null) f.setTipo(texto(d, "tipo").trim());
        if (d.get("activo") != null) f.setActivo(bool(d, "activo"));
        if (f.getFecha() == null) throw new IllegalArgumentException("La fecha es requerida");
        requerido(f.getDescripcion(), "La descripcion es requerida");
        return feriadoRepository.save(f);
    }

    /** Elimina el feriado; si ya tiene feriados laborados registrados solo se desactiva. */
    @Transactional
    public String eliminarFeriado(Integer id) {
        DiaFeriado f = feriadoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Feriado no encontrado"));
        try {
            feriadoRepository.delete(f);
            feriadoRepository.flush();
            return "Feriado eliminado";
        } catch (Exception e) {
            throw new IllegalStateException("El feriado tiene registros de feriados laborados. Desactívelo en lugar de eliminarlo");
        }
    }

    // ==================== DEPARTAMENTOS ====================

    @Transactional(readOnly = true)
    public List<Departamento> departamentos() {
        return departamentoRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public Departamento guardarDepartamento(Integer id, Map<String, Object> d) {
        Departamento dep = id == null ? new Departamento()
                : departamentoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
        String nombre = texto(d, "nombre");
        if (nombre != null) {
            nombre = nombre.trim();
            if (!nombre.equalsIgnoreCase(dep.getNombre()) && departamentoRepository.existsByNombreIgnoreCase(nombre)) {
                throw new IllegalArgumentException("Ya existe un departamento con ese nombre");
            }
            dep.setNombre(nombre);
        }
        if (d.containsKey("descripcion")) dep.setDescripcion(texto(d, "descripcion"));
        if (d.containsKey("responsableId")) {
            Integer resp = entero(d, "responsableId");
            if (resp != null && !empleadoRepository.existsById(resp)) {
                throw new IllegalArgumentException("Empleado responsable no encontrado");
            }
            dep.setResponsableId(resp);
        }
        if (d.get("activo") != null) dep.setActivo(bool(d, "activo"));
        requerido(dep.getNombre(), "El nombre es requerido");
        return departamentoRepository.save(dep);
    }

    // ==================== ROLES Y APROBACIONES ====================

    @Transactional(readOnly = true)
    public List<TipoUsuario> tiposUsuario() {
        return tipoUsuarioRepository.findAllByOrderByNivelJerarquiaDesc();
    }

    /** Edita atributos de un rol existente (el codigo no se cambia porque lo usa la seguridad). */
    @Transactional
    public TipoUsuario actualizarTipoUsuario(Integer id, Map<String, Object> d) {
        TipoUsuario t = tipoUsuarioRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Rol no encontrado"));
        if (Boolean.TRUE.equals(t.getEsSistema())) {
            throw new IllegalArgumentException("El rol del administrador no se puede modificar");
        }
        if (texto(d, "nombre") != null) t.setNombre(texto(d, "nombre").trim());
        if (d.containsKey("descripcion")) t.setDescripcion(texto(d, "descripcion"));
        if (d.get("nivelJerarquia") != null) t.setNivelJerarquia(entero(d, "nivelJerarquia"));
        if (d.get("puedeSolicitar") != null) t.setPuedeSolicitar(bool(d, "puedeSolicitar"));
        if (d.get("marcaAsistencia") != null) t.setMarcaAsistencia(bool(d, "marcaAsistencia"));
        if (d.get("activo") != null) t.setActivo(bool(d, "activo"));
        return tipoUsuarioRepository.save(t);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reglasAprobacion() {
        return reglaRepository.findAll().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("solicitanteId", r.getSolicitante().getId());
            m.put("solicitante", r.getSolicitante().getCodigo());
            m.put("aprobadorId", r.getAprobador().getId());
            m.put("aprobador", r.getAprobador().getCodigo());
            m.put("activo", r.getActivo());
            return m;
        }).toList();
    }

    @Transactional
    public ReglaAprobacion crearRegla(Integer solicitanteId, Integer aprobadorId) {
        TipoUsuario sol = tipoUsuarioRepository.findById(solicitanteId)
                .orElseThrow(() -> new IllegalArgumentException("Rol solicitante no encontrado"));
        TipoUsuario apr = tipoUsuarioRepository.findById(aprobadorId)
                .orElseThrow(() -> new IllegalArgumentException("Rol aprobador no encontrado"));
        if (Boolean.TRUE.equals(sol.getEsSistema()) || Boolean.TRUE.equals(apr.getEsSistema())) {
            throw new IllegalArgumentException("El administrador no participa en las aprobaciones");
        }
        if (reglaRepository.existsBySolicitanteIdAndAprobadorId(solicitanteId, aprobadorId)) {
            throw new IllegalArgumentException("La regla ya existe");
        }
        ReglaAprobacion r = new ReglaAprobacion();
        r.setSolicitante(sol);
        r.setAprobador(apr);
        return reglaRepository.save(r);
    }

    @Transactional
    public void eliminarRegla(Integer id) {
        reglaRepository.deleteById(id);
    }

    // ==================== CATALOGOS DE SOLICITUDES ====================

    @Transactional(readOnly = true)
    public List<TipoSolicitud> tiposSolicitud() {
        return tipoSolicitudRepository.findAll();
    }

    /** Edita nombre, descripcion, si exige evidencia y si esta activo (el saldo que descuenta es fijo). */
    @Transactional
    public TipoSolicitud actualizarTipoSolicitud(Integer id, Map<String, Object> d) {
        TipoSolicitud t = tipoSolicitudRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de solicitud no encontrado"));
        if (texto(d, "nombre") != null) t.setNombre(texto(d, "nombre").trim());
        if (d.containsKey("descripcion")) t.setDescripcion(texto(d, "descripcion"));
        if (d.get("requiereEvidencia") != null) t.setRequiereEvidencia(bool(d, "requiereEvidencia"));
        if (d.get("activo") != null) t.setActivo(bool(d, "activo"));
        return tipoSolicitudRepository.save(t);
    }

    @Transactional(readOnly = true)
    public List<MotivoLicencia> motivosLicencia() {
        return motivoLicenciaRepository.findAll();
    }

    @Transactional
    public MotivoLicencia guardarMotivoLicencia(Integer id, Map<String, Object> d) {
        MotivoLicencia m = id == null ? new MotivoLicencia()
                : motivoLicenciaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Motivo no encontrado"));
        if (texto(d, "nombre") != null) m.setNombre(texto(d, "nombre").trim());
        if (d.containsKey("descripcion")) m.setDescripcion(texto(d, "descripcion"));
        if (d.get("activo") != null) m.setActivo(bool(d, "activo"));
        requerido(m.getNombre(), "El nombre es requerido");
        return motivoLicenciaRepository.save(m);
    }

    // ==================== USUARIOS ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usuarios() {
        return usuarioRepository.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("rol", u.getRol());
            m.put("rolNombre", u.getTipoUsuario().getNombre());
            m.put("activo", u.getActivo());
            m.put("debeCambiarPassword", u.getDebeCambiarPassword());
            m.put("ultimoAcceso", u.getUltimoAcceso());
            Empleado e = u.getEmpleado();
            m.put("empleadoId", e != null ? e.getId() : null);
            m.put("empleadoNombre", e != null ? e.getNombre() : null);
            m.put("departamento", e != null ? e.getDepartamentoNombre() : null);
            m.put("empleadoActivo", e != null ? e.getActivo() : null);
            return m;
        }).toList();
    }

    @Transactional
    public Usuario cambiarRol(Integer usuarioId, String rol) {
        Usuario u = usuarioNoAdmin(usuarioId);
        u.setTipoUsuario(usuarioService.tipoPersonal(rol));
        return usuarioRepository.save(u);
    }

    @Transactional
    public Usuario cambiarEstadoUsuario(Integer usuarioId, boolean activo) {
        Usuario u = usuarioNoAdmin(usuarioId);
        u.setActivo(activo);
        return usuarioRepository.save(u);
    }

    @Transactional
    public Usuario restablecerPassword(Integer usuarioId, String nueva) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return usuarioService.restablecerPassword(u, nueva);
    }

    private Usuario usuarioNoAdmin(Integer usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (Roles.esAdmin(u.getRol())) {
            throw new IllegalArgumentException("La cuenta de administrador no se puede modificar desde aqui");
        }
        return u;
    }

    // ==================== SALDOS ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> saldosTodos() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Empleado e : empleadoRepository.findAll()) {
            Map<String, SaldoResumenDTO> resumen = saldoService.resumen(e.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("empleadoId", e.getId());
            m.put("empleadoNombre", e.getNombre());
            m.put("dni", e.getDni());
            m.put("rol", e.getRol());
            m.put("activo", e.getActivo());
            m.put("ingreso", e.getIngreso());
            m.put("vacaciones", resumen.get("vacaciones"));
            m.put("compensacion", resumen.get("compensacion"));
            r.add(m);
        }
        return r;
    }

    /**
     * Carga inicial: deja los saldos exactamente en los valores indicados (dias que se le deben
     * al empleado a la fecha). Se puede enviar uno o ambos valores.
     */
    @Transactional
    public Map<String, SaldoResumenDTO> cargaInicial(Integer empleadoId, BigDecimal vacaciones,
                                                     BigDecimal compensacion, String observacion) {
        Empleado e = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        if (vacaciones == null && compensacion == null) {
            throw new IllegalArgumentException("Indique los dias de vacaciones y/o de compensacion");
        }
        Usuario admin = usuarioActual.requerido();
        String obs = observacion == null || observacion.isBlank() ? "Carga inicial de saldo" : observacion.trim();
        if (vacaciones != null) {
            saldoService.establecerSaldo(e, TipoSaldo.VACACIONES, vacaciones, TipoMovimiento.CARGA_INICIAL, obs, admin);
        }
        if (compensacion != null) {
            saldoService.establecerSaldo(e, TipoSaldo.COMPENSACION, compensacion, TipoMovimiento.CARGA_INICIAL, obs, admin);
        }
        return saldoService.resumen(empleadoId);
    }

    /** Ajuste manual: suma (positivo) o resta (negativo) dias a un saldo, con motivo obligatorio. */
    @Transactional
    public Map<String, SaldoResumenDTO> ajustarSaldo(Integer empleadoId, TipoSaldo tipo, BigDecimal dias, String observacion) {
        Empleado e = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        if (tipo == null) throw new IllegalArgumentException("Indique el tipo de saldo (VACACIONES o COMPENSACION)");
        if (dias == null || dias.signum() == 0) throw new IllegalArgumentException("Indique los dias a ajustar (distinto de 0)");
        requerido(observacion, "El motivo del ajuste es obligatorio");
        saldoService.registrar(e, tipo, TipoMovimiento.AJUSTE, dias, Origen.PANEL_ADMIN, observacion.trim(),
                usuarioActual.requerido(), null, null, null, false);
        return saldoService.resumen(empleadoId);
    }

    // ==================== UTILIDADES ====================

    private static String texto(Map<String, Object> d, String k) {
        Object v = d.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean bool(Map<String, Object> d, String k) {
        Object v = d.get(k);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static Integer entero(Map<String, Object> d, String k) {
        Object v = d.get(k);
        if (v == null || String.valueOf(v).isBlank()) return null;
        return v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v).trim());
    }

    private static void requerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensaje);
    }
}
