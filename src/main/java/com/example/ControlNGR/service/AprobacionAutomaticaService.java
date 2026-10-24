package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.repository.AprobacionAutomaticaRepository;
import com.example.ControlNGR.repository.DepartamentoRepository;
import com.example.ControlNGR.repository.HorarioSemanalDetalleRepository;
import com.example.ControlNGR.repository.SolicitudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Aprobación automática de compensaciones por equipo (departamento).
 * Una compensación se aprueba sola si cumple, en este orden: la antelación mínima, el máximo de
 * días por empleado en la semana, el cupo semanal del equipo y la cobertura mínima de cada día
 * (según el horario publicado). Si no cumple alguna regla queda en la sala de espera (pendiente)
 * para que la decida el supervisor; con el cupo del equipo lleno puede rechazarse, si el equipo
 * así lo tiene configurado. Las urgencias personales siempre van al supervisor.
 */
@Service
public class AprobacionAutomaticaService {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DDMM = DateTimeFormatter.ofPattern("dd/MM");
    static final String TIPO_COMPENSACION = "compensacion";
    /** Inicio del motivo de espera por cupo lleno: solo esas solicitudes tienen turno en la fila. */
    static final String MOTIVO_CUPO = "Cupo semanal del equipo completo";

    /** True si la solicitud espera que se libere un cupo del equipo (las demás esperan al supervisor). */
    public static boolean esperaCupo(Solicitud s) {
        return s.getMotivoEspera() != null && s.getMotivoEspera().startsWith(MOTIVO_CUPO);
    }

    public enum Accion { APROBAR, ESPERA, RECHAZAR }

    public record Resultado(Accion accion, String motivo) {
        static Resultado aprobar(String motivo) { return new Resultado(Accion.APROBAR, motivo); }
        static Resultado espera(String motivo) { return new Resultado(Accion.ESPERA, motivo); }
        static Resultado rechazar(String motivo) { return new Resultado(Accion.RECHAZAR, motivo); }
    }

    /** Días de la solicitud y días ya aprobados en una semana (lunes a domingo). */
    record Semana(LocalDate lunes, int diasSolicitud, int diasEmpleado, int diasEquipo) {}

    /** Cobertura de un día: si el horario está publicado, si el solicitante tiene turno y cuántos hay en turno (con él). */
    record Dia(LocalDate fecha, boolean publicado, boolean tieneTurno, int enTurno) {}

    private final AprobacionAutomaticaRepository repository;
    private final DepartamentoRepository departamentoRepository;
    private final SolicitudRepository solicitudRepository;
    private final HorarioSemanalDetalleRepository detalleRepository;

    public AprobacionAutomaticaService(AprobacionAutomaticaRepository repository,
                                       DepartamentoRepository departamentoRepository,
                                       SolicitudRepository solicitudRepository,
                                       HorarioSemanalDetalleRepository detalleRepository) {
        this.repository = repository;
        this.departamentoRepository = departamentoRepository;
        this.solicitudRepository = solicitudRepository;
        this.detalleRepository = detalleRepository;
    }

    public static boolean esCompensacion(Solicitud s) {
        return TIPO_COMPENSACION.equals(s.getTipo());
    }

    /**
     * Configuración activa del equipo, bloqueada hasta el fin de la transacción para que dos
     * solicitudes del mismo equipo no ocupen a la vez el último cupo.
     */
    public Optional<AprobacionAutomatica> bloquearConfiguracionActiva(Empleado empleado) {
        Integer depto = empleado.getDepartamentoId();
        if (depto == null) return Optional.empty();
        return repository.bloquearPorDepartamento(depto).filter(c -> Boolean.TRUE.equals(c.getActivo()));
    }

    // ==================== REGLAS ====================

    /** Evalúa una compensación con los datos actuales del equipo (la solicitud aún no está aprobada). */
    @Transactional(readOnly = true)
    public Resultado evaluar(Solicitud solicitud, AprobacionAutomatica cfg) {
        Empleado empleado = solicitud.getEmpleado();
        Integer depto = empleado.getDepartamentoId();
        LocalDate inicio = solicitud.getFechaInicio(), fin = solicitud.getFechaFin();

        // Días ya aprobados por semana, del empleado y del equipo
        LocalDate desde = lunes(inicio), hasta = lunes(fin).plusDays(6);
        List<Solicitud> aprobadasEquipo = solicitudRepository.findCompensacionesAprobadasDeEquipo(depto, desde, hasta).stream()
                .filter(s -> !s.getId().equals(solicitud.getId())).toList();
        List<Semana> semanas = new ArrayList<>();
        for (LocalDate l = desde; !l.isAfter(fin); l = l.plusWeeks(1)) {
            LocalDate d = l, h = l.plusDays(6);
            int equipo = aprobadasEquipo.stream().mapToInt(s -> diasEnRango(s, d, h)).sum();
            int propios = aprobadasEquipo.stream().filter(s -> s.getEmpleado().getId().equals(empleado.getId()))
                    .mapToInt(s -> diasEnRango(s, d, h)).sum();
            semanas.add(new Semana(l, diasEnRango(solicitud, d, h), propios, equipo));
        }

        // Cobertura de cada día según el horario publicado
        List<Dia> dias = new ArrayList<>();
        for (LocalDate f = inicio; !f.isAfter(fin); f = f.plusDays(1)) {
            dias.add(cobertura(depto, empleado.getId(), f));
        }
        LocalDateTime pedida = solicitud.getFechaSolicitud() != null ? solicitud.getFechaSolicitud() : LocalDateTime.now(LIMA);
        return evaluar(cfg, Boolean.TRUE.equals(solicitud.getUrgente()), pedida, inicio, semanas, dias);
    }

    /** Reglas en orden: antelación, máximo por empleado, cupo del equipo y cobertura mínima. */
    static Resultado evaluar(AprobacionAutomatica cfg, boolean urgente, LocalDateTime pedida, LocalDate inicio,
                             List<Semana> semanas, List<Dia> dias) {
        if (urgente) {
            return Resultado.espera("Urgencia personal: la revisa su supervisor");
        }
        long horas = ChronoUnit.HOURS.between(pedida, inicio.atStartOfDay());
        if (horas < cfg.getAntelacionHoras()) {
            return Resultado.espera("Solicitada con menos de " + cfg.getAntelacionHoras() + " h de antelación");
        }
        for (Semana s : semanas) {
            if (s.diasEmpleado() + s.diasSolicitud() > cfg.getMaximoPorEmpleado()) {
                return Resultado.espera("Supera el máximo de " + cfg.getMaximoPorEmpleado()
                        + " día(s) de compensación por semana (semana del " + s.lunes().format(DDMM) + ")");
            }
        }
        for (Semana s : semanas) {
            if (s.diasEquipo() + s.diasSolicitud() > cfg.getCupoSemanal()) {
                String motivo = MOTIVO_CUPO + ": " + s.diasEquipo() + " de " + cfg.getCupoSemanal()
                        + " día(s) usados (semana del " + s.lunes().format(DDMM) + ")";
                return AprobacionAutomatica.RECHAZO.equals(cfg.getAlLlenarse()) ? Resultado.rechazar(motivo) : Resultado.espera(motivo);
            }
        }
        for (Dia d : dias) {
            if (!d.publicado()) {
                return Resultado.espera("El horario del " + d.fecha().format(DDMM) + " aún no está publicado");
            }
            if (!d.tieneTurno()) {
                return Resultado.espera("No tiene turno laboral el " + d.fecha().format(DDMM));
            }
            if (d.enTurno() - 1 < cfg.getMinimoEnTurno()) {
                return Resultado.espera("Cobertura mínima: el " + d.fecha().format(DDMM) + " quedarían "
                        + (d.enTurno() - 1) + " en turno (mínimo " + cfg.getMinimoEnTurno() + ")");
            }
        }
        Semana s = semanas.get(0);
        return Resultado.aprobar("Aprobada automáticamente. Cupo del equipo: " + (s.diasEquipo() + s.diasSolicitud())
                + " de " + cfg.getCupoSemanal() + " día(s) en la semana del " + s.lunes().format(DDMM));
    }

    /** Quiénes del equipo tienen turno laboral ese día, sin contar a los que ya tienen una solicitud aprobada. */
    private Dia cobertura(Integer depto, Integer solicitanteId, LocalDate fecha) {
        List<HorarioSemanalDetalle> detalles = detalleRepository.findPublicadosDeEquipoEnFecha(depto, fecha);
        if (detalles.isEmpty()) return new Dia(fecha, false, false, 0);
        Set<Integer> enTurno = new HashSet<>();
        for (HorarioSemanalDetalle d : detalles) {
            if (esLaboral(d)) enTurno.add(d.getEmpleado().getId());
        }
        boolean tieneTurno = enTurno.contains(solicitanteId);
        for (Solicitud s : solicitudRepository.findAprobadasDeEquipo(depto, fecha, fecha)) {
            if (!s.getEmpleado().getId().equals(solicitanteId)) enTurno.remove(s.getEmpleado().getId());
        }
        return new Dia(fecha, true, tieneTurno, enTurno.size());
    }

    /** Día laboral; también el marcado por una solicitud que ya no está aprobada (se restaura al confirmar). */
    private static boolean esLaboral(HorarioSemanalDetalle d) {
        if ("normal".equalsIgnoreCase(d.getTipoDia())) return true;
        Solicitud ref = d.getSolicitudRef();
        return "solicitud_aprobada".equals(d.getOrigenTipoDia()) && ref != null && !Solicitud.APROBADO.equals(ref.getEstado());
    }

    static LocalDate lunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static int diasEnRango(Solicitud s, LocalDate desde, LocalDate hasta) {
        LocalDate i = s.getFechaInicio().isAfter(desde) ? s.getFechaInicio() : desde;
        LocalDate f = s.getFechaFin().isBefore(hasta) ? s.getFechaFin() : hasta;
        return f.isBefore(i) ? 0 : (int) ChronoUnit.DAYS.between(i, f) + 1;
    }

    // ==================== CUPOS PARA EL COLABORADOR ====================

    /** Cupos de la semana de la fecha indicada, para mostrarlos antes de enviar la solicitud. */
    @Transactional(readOnly = true)
    public Map<String, Object> cupos(Empleado empleado, LocalDate fecha) {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<AprobacionAutomatica> cfg = Optional.ofNullable(empleado.getDepartamentoId())
                .flatMap(repository::findByDepartamentoId).filter(c -> Boolean.TRUE.equals(c.getActivo()));
        m.put("activo", cfg.isPresent());
        if (cfg.isEmpty()) return m;
        LocalDate l = lunes(fecha == null ? LocalDate.now(LIMA) : fecha), d = l.plusDays(6);
        List<Solicitud> aprobadas = solicitudRepository.findCompensacionesAprobadasDeEquipo(empleado.getDepartamentoId(), l, d);
        int equipo = aprobadas.stream().mapToInt(s -> diasEnRango(s, l, d)).sum();
        int propios = aprobadas.stream().filter(s -> s.getEmpleado().getId().equals(empleado.getId()))
                .mapToInt(s -> diasEnRango(s, l, d)).sum();
        m.put("equipo", empleado.getDepartamentoNombre());
        m.put("semanaInicio", l.toString());
        m.put("semanaFin", d.toString());
        m.put("cupoSemanal", cfg.get().getCupoSemanal());
        m.put("usadosEquipo", equipo);
        m.put("maximoPorEmpleado", cfg.get().getMaximoPorEmpleado());
        m.put("usadosEmpleado", propios);
        m.put("antelacionHoras", cfg.get().getAntelacionHoras());
        return m;
    }

    // ==================== CONFIGURACIÓN (PANEL ADMIN) ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        Map<Integer, AprobacionAutomatica> porDepto = new HashMap<>();
        repository.findAll().forEach(c -> porDepto.put(c.getDepartamento().getId(), c));
        List<Map<String, Object>> lista = new ArrayList<>();
        departamentoRepository.findAll().stream()
                .filter(d -> Boolean.TRUE.equals(d.getActivo()) || porDepto.containsKey(d.getId()))
                .sorted(Comparator.comparing(Departamento::getNombre))
                .forEach(d -> lista.add(mapa(d, porDepto.get(d.getId()))));
        return lista;
    }

    @Transactional
    public Map<String, Object> guardar(Integer departamentoId, Map<String, Object> body, String usuario) {
        Departamento depto = departamentoRepository.findById(departamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
        AprobacionAutomatica c = repository.findByDepartamentoId(departamentoId).orElseGet(() -> {
            AprobacionAutomatica n = new AprobacionAutomatica();
            n.setDepartamento(depto);
            return n;
        });
        if (body.containsKey("activo")) c.setActivo(Boolean.TRUE.equals(body.get("activo")));
        c.setCupoSemanal(entero(body, "cupoSemanal", c.getCupoSemanal(), 0, 50, "Días de compensación por semana"));
        c.setMaximoPorEmpleado(entero(body, "maximoPorEmpleado", c.getMaximoPorEmpleado(), 0, 7, "Máximo por empleado"));
        c.setMinimoEnTurno(entero(body, "minimoEnTurno", c.getMinimoEnTurno(), 0, 100, "Mínimo de personas en turno"));
        c.setAntelacionHoras(entero(body, "antelacionHoras", c.getAntelacionHoras(), 0, 720, "Antelación mínima"));
        if (body.containsKey("alLlenarse")) {
            String v = String.valueOf(body.get("alLlenarse")).trim().toUpperCase();
            if (!AprobacionAutomatica.ESPERA.equals(v) && !AprobacionAutomatica.RECHAZO.equals(v)) {
                throw new IllegalArgumentException("Al llenarse el cupo: use ESPERA o RECHAZO");
            }
            c.setAlLlenarse(v);
        }
        if (body.containsKey("promoverEspera")) c.setPromoverEspera(Boolean.TRUE.equals(body.get("promoverEspera")));
        c.setActualizadoEn(LocalDateTime.now(LIMA));
        c.setActualizadoPor(usuario);
        return mapa(depto, repository.save(c));
    }

    private static int entero(Map<String, Object> body, String clave, int actual, int min, int max, String nombre) {
        if (!body.containsKey(clave) || body.get(clave) == null) return actual;
        int v;
        try {
            v = (int) Math.round(Double.parseDouble(String.valueOf(body.get(clave))));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nombre + ": ingrese un número");
        }
        if (v < min || v > max) throw new IllegalArgumentException(nombre + ": debe estar entre " + min + " y " + max);
        return v;
    }

    private static Map<String, Object> mapa(Departamento d, AprobacionAutomatica c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("departamentoId", d.getId());
        m.put("departamento", d.getNombre());
        m.put("configurado", c != null);
        m.put("activo", c != null && Boolean.TRUE.equals(c.getActivo()));
        m.put("cupoSemanal", c != null ? c.getCupoSemanal() : 4);
        m.put("maximoPorEmpleado", c != null ? c.getMaximoPorEmpleado() : 1);
        m.put("minimoEnTurno", c != null ? c.getMinimoEnTurno() : 1);
        m.put("antelacionHoras", c != null ? c.getAntelacionHoras() : 48);
        m.put("alLlenarse", c != null ? c.getAlLlenarse() : AprobacionAutomatica.ESPERA);
        m.put("promoverEspera", c == null || Boolean.TRUE.equals(c.getPromoverEspera()));
        m.put("actualizadoEn", c != null ? c.getActualizadoEn() : null);
        m.put("actualizadoPor", c != null ? c.getActualizadoPor() : null);
        return m;
    }
}
