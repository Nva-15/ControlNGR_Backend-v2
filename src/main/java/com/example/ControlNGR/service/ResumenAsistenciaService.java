package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Asistencia;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Horario;
import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import com.example.ControlNGR.repository.AsistenciaRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.HorarioRepository;
import com.example.ControlNGR.repository.HorarioSemanalDetalleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Resumen diario de asistencia: por persona y día, el resultado frente a su horario programado (a tiempo,
 * tardanza, falta, descanso...). Es la base del dashboard y del récord del colaborador, para analizar años
 * de historia sin recalcular cada consulta. Se calcula por mes con pocas consultas (todo en memoria) y se
 * mantiene al día: al iniciar (historial completo si está vacío), cada 15 minutos (hoy y ayer) y cada
 * noche (últimos 35 días, por si se corrigieron horarios o solicitudes).
 */
@Service
public class ResumenAsistenciaService {

    private static final Logger logger = LoggerFactory.getLogger(ResumenAsistenciaService.class);

    public static final String A_TIEMPO = "a_tiempo";
    public static final String TARDANZA = "tardanza";
    public static final String FALTA = "falta";
    public static final String PENDIENTE = "pendiente";
    public static final String PERMISO = "permiso";
    /** Marcó un día sin horario (rol sin horario o sin turno cargado). */
    public static final String ASISTIO = "asistio";

    private static final int DIAS_A_REVISAR = 35;
    private static final int DIA = 24 * 60;

    private final JdbcTemplate jdbc;
    private final AsistenciaRepository asistenciaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final HorarioRepository horarioRepository;
    private final HorarioSemanalDetalleRepository detalleRepository;
    private final ParametroService parametroService;
    private final TransactionTemplate transaccion;
    private final ReentrantLock calculando = new ReentrantLock();

    public ResumenAsistenciaService(JdbcTemplate jdbc, AsistenciaRepository asistenciaRepository,
                                    EmpleadoRepository empleadoRepository, HorarioRepository horarioRepository,
                                    HorarioSemanalDetalleRepository detalleRepository, ParametroService parametroService,
                                    PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.asistenciaRepository = asistenciaRepository;
        this.empleadoRepository = empleadoRepository;
        this.horarioRepository = horarioRepository;
        this.detalleRepository = detalleRepository;
        this.parametroService = parametroService;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /** Un día de una persona ya clasificado. */
    record Dia(int empleadoId, LocalDate fecha, Integer departamentoId, String rol, String estado, String tipoDia,
               String turno, String funciones, LocalTime entradaProgramada, LocalTime salidaProgramada,
               LocalTime entradaReal, LocalTime salidaReal, Integer minutosDiferencia, Integer minutosRetraso,
               Integer minutosProgramados, Integer minutosTrabajados, boolean salidaAutomatica, String metodoEntrada) {}

    /** Lo programado para el día: horario semanal publicado (activo o histórico) o, si no hay, el horario base. */
    record Programado(String tipoDia, String turno, String funciones, LocalTime entrada, LocalTime salida) {}

    // ==================== TAREAS ====================

    /** Al iniciar: si el resumen está vacío calcula todo el historial; si no, los últimos días. En segundo plano. */
    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        Thread hilo = new Thread(() -> {
            try {
                Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM asistencia_resumen_diario", Integer.class);
                if (filas == null || filas == 0) recalcularHistorial(); else recalcularRecientes();
            } catch (Exception e) {
                logger.error("Error al preparar el resumen de asistencia: {}", e.getMessage(), e);
            }
        }, "resumen-asistencia");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Durante el día: hoy y ayer (los turnos de noche terminan al día siguiente). */
    @Scheduled(cron = "0 */15 * * * *", zone = "America/Lima")
    public void cadaQuinceMinutos() {
        LocalDate hoy = LocalDate.now();
        ejecutar(() -> recalcular(hoy.minusDays(1), hoy));
    }

    /** Cada noche a las 00:40: los últimos 35 días, por si cambiaron horarios, solicitudes o marcaciones. */
    @Scheduled(cron = "0 40 0 * * *", zone = "America/Lima")
    public void cadaNoche() {
        ejecutar(this::recalcularRecientes);
    }

    private void recalcularRecientes() {
        LocalDate hoy = LocalDate.now();
        recalcular(hoy.minusDays(DIAS_A_REVISAR), hoy);
    }

    /** Todo el historial, mes por mes, desde la primera marcación u horario registrado. Devuelve los días calculados. */
    public int recalcularHistorial() {
        LocalDate desde = primeraFecha();
        return recalcularDesde(desde);
    }

    public int recalcularDesde(LocalDate desde) {
        LocalDate hoy = LocalDate.now();
        if (desde == null || desde.isAfter(hoy)) return 0;
        int total = 0;
        long inicio = System.currentTimeMillis();
        for (LocalDate mes = desde.withDayOfMonth(1); !mes.isAfter(hoy); mes = mes.plusMonths(1)) {
            LocalDate a = mes.isBefore(desde) ? desde : mes;
            LocalDate b = mes.plusMonths(1).minusDays(1);
            total += recalcular(a, b.isAfter(hoy) ? hoy : b);
        }
        logger.info("Resumen de asistencia: {} día(s) calculados desde {} en {} s", total, desde,
                (System.currentTimeMillis() - inicio) / 1000);
        return total;
    }

    private void ejecutar(Runnable tarea) {
        try {
            tarea.run();
        } catch (Exception e) {
            logger.error("Error al actualizar el resumen de asistencia: {}", e.getMessage(), e);
        }
    }

    private LocalDate primeraFecha() {
        LocalDate asistencia = jdbc.queryForObject("SELECT MIN(fecha) FROM asistencia", LocalDate.class);
        LocalDate semana = jdbc.queryForObject("SELECT MIN(fecha) FROM horarios_semanales_detalle", LocalDate.class);
        if (asistencia == null) return semana;
        if (semana == null) return asistencia;
        return asistencia.isBefore(semana) ? asistencia : semana;
    }

    // ==================== CÁLCULO ====================

    /** Recalcula el rango (máximo un mes por llamada para acotar la memoria) y reemplaza sus filas. */
    public int recalcular(LocalDate desde, LocalDate hasta) {
        calculando.lock();
        try {
            List<Dia> dias = transaccion.execute(status -> calcular(desde, hasta, LocalDateTime.now()));
            guardar(desde, hasta, dias == null ? List.of() : dias);
            return dias == null ? 0 : dias.size();
        } finally {
            calculando.unlock();
        }
    }

    List<Dia> calcular(LocalDate desde, LocalDate hasta, LocalDateTime ahora) {
        int tolerancia = Math.max(0, parametroService.entero(ParametroService.TOLERANCIA_TARDANZA_MINUTOS, 10));

        Map<String, Asistencia> asistencias = new HashMap<>();
        for (Asistencia a : asistenciaRepository.findByFechaBetween(desde, hasta)) {
            asistencias.putIfAbsent(a.getEmpleado().getId() + "|" + a.getFecha(), a);
        }
        Map<String, HorarioSemanalDetalle> semanal = new HashMap<>();
        for (HorarioSemanalDetalle d : detalleRepository.findPublicadosEntre(desde, hasta)) {
            semanal.put(d.getEmpleado().getId() + "|" + d.getFecha(), d);
        }
        Map<String, Horario> base = new HashMap<>();
        for (Horario h : horarioRepository.findAll()) {
            base.put(h.getEmpleado().getId() + "|" + h.getDiaSemana(), h);
        }
        // Última marcación de cada persona: el historial de quien ya no está activo llega hasta ahí
        Map<Integer, LocalDate> ultimaMarcacion = new HashMap<>();
        jdbc.query("SELECT empleado_id, MAX(fecha) FROM asistencia GROUP BY empleado_id",
                rs -> { ultimaMarcacion.put(rs.getInt(1), rs.getDate(2).toLocalDate()); });

        Set<Integer> conMarcacion = new HashSet<>();
        for (Asistencia a : asistencias.values()) conMarcacion.add(a.getEmpleado().getId());

        List<Dia> resultado = new ArrayList<>();
        for (Empleado e : empleadoRepository.findAll()) {
            boolean activo = Boolean.TRUE.equals(e.getActivo());
            if (!e.tieneHorario() && !conMarcacion.contains(e.getId())) continue;
            LocalDate fin = hasta;
            if (!activo) {
                LocalDate ultima = ultimaMarcacion.get(e.getId());
                if (ultima == null) continue;
                if (ultima.isBefore(fin)) fin = ultima;
            }
            LocalDate inicio = e.getIngreso() != null && e.getIngreso().isAfter(desde) ? e.getIngreso() : desde;
            for (LocalDate f = inicio; !f.isAfter(fin); f = f.plusDays(1)) {
                Programado p = programado(e, f, semanal, base);
                Dia dia = clasificar(e, f, p, asistencias.get(e.getId() + "|" + f), tolerancia, ahora);
                if (dia != null) resultado.add(dia);
            }
        }
        return resultado;
    }

    private static Programado programado(Empleado e, LocalDate fecha, Map<String, HorarioSemanalDetalle> semanal,
                                         Map<String, Horario> base) {
        if (!e.tieneHorario()) return null;
        HorarioSemanalDetalle d = semanal.get(e.getId() + "|" + fecha);
        if (d != null) return new Programado(d.getTipoDia(), d.getTurno(), d.getFunciones(), d.getHoraEntrada(), d.getHoraSalida());
        Horario h = base.get(e.getId() + "|" + diaSemana(fecha));
        if (h != null) return new Programado(h.getTipoDia(), h.getTurno(), h.getFunciones(), h.getHoraEntrada(), h.getHoraSalida());
        return null;
    }

    /**
     * Resultado del día, con las mismas reglas que el reporte de asistencia: día no laboral → su tipo;
     * laboral con entrada → a tiempo o tardanza (tolerancia); sin entrada → falta (o pendiente si su turno
     * de hoy aún no empieza); sin horario → "asistió" si marcó, y si no marcó el día no cuenta.
     */
    static Dia clasificar(Empleado e, LocalDate fecha, Programado p, Asistencia a, int tolerancia, LocalDateTime ahora) {
        boolean marco = a != null && a.getHoraEntrada() != null;
        boolean permiso = a != null && "permiso".equalsIgnoreCase(a.getEstado());
        String tipoDia = p == null ? null : (p.tipoDia() == null ? "normal" : p.tipoDia().toLowerCase());
        String estado;
        Integer diferencia = null;
        Integer retraso = null;

        if (p == null) {
            if (marco) estado = ASISTIO;
            else if (permiso) estado = PERMISO;
            else return null;
        } else if (!"normal".equals(tipoDia)) {
            estado = tipoDia;
        } else if (marco) {
            if (p.entrada() != null) {
                diferencia = diferenciaMinutos(p.entrada(), a.getHoraEntrada());
                boolean tarde = diferencia > tolerancia;
                estado = tarde ? TARDANZA : A_TIEMPO;
                retraso = tarde ? diferencia : 0;
            } else {
                estado = A_TIEMPO;
                retraso = 0;
            }
        } else if (permiso) {
            estado = PERMISO;
        } else if (fecha.isAfter(ahora.toLocalDate())
                || (fecha.equals(ahora.toLocalDate()) && (p.entrada() == null
                    || ahora.toLocalTime().isBefore(p.entrada().plusMinutes(tolerancia))))) {
            estado = PENDIENTE;
        } else {
            estado = FALTA;
        }

        boolean laboral = p != null && "normal".equals(tipoDia);
        Integer programados = laboral ? duracion(p.entrada(), p.salida()) : null;
        boolean auto = a != null && Boolean.TRUE.equals(a.getSalidaAutomatica());
        Integer trabajados = marco && a.getHoraSalida() != null && !auto ? duracion(a.getHoraEntrada(), a.getHoraSalida()) : null;
        return new Dia(e.getId(), fecha, e.getDepartamento() != null ? e.getDepartamento().getId() : null, e.getRol(),
                estado, tipoDia, laboral ? p.turno() : null, laboral ? p.funciones() : null,
                laboral ? p.entrada() : null, laboral ? p.salida() : null,
                marco ? a.getHoraEntrada() : null, marco ? a.getHoraSalida() : null,
                diferencia, retraso, programados, trabajados, marco && auto, marco ? a.getMetodoEntrada() : null);
    }

    /** Minutos de la entrada real respecto a la programada (negativo = antes); contempla turnos cerca de la medianoche. */
    static int diferenciaMinutos(LocalTime programada, LocalTime real) {
        int d = (real.toSecondOfDay() - programada.toSecondOfDay()) / 60;
        if (d > DIA / 2) d -= DIA;
        if (d < -DIA / 2) d += DIA;
        return d;
    }

    private static Integer duracion(LocalTime desde, LocalTime hasta) {
        if (desde == null || hasta == null) return null;
        int m = (hasta.toSecondOfDay() - desde.toSecondOfDay()) / 60;
        return m <= 0 ? m + DIA : m;
    }

    private static String diaSemana(LocalDate fecha) {
        return switch (fecha.getDayOfWeek()) {
            case MONDAY -> "lunes";
            case TUESDAY -> "martes";
            case WEDNESDAY -> "miercoles";
            case THURSDAY -> "jueves";
            case FRIDAY -> "viernes";
            case SATURDAY -> "sabado";
            case SUNDAY -> "domingo";
        };
    }

    private void guardar(LocalDate desde, LocalDate hasta, List<Dia> dias) {
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
        transaccion.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM asistencia_resumen_diario WHERE fecha BETWEEN ? AND ?", Date.valueOf(desde), Date.valueOf(hasta));
            jdbc.batchUpdate("INSERT INTO asistencia_resumen_diario (empleado_id, fecha, departamento_id, rol, estado, tipo_dia, turno, "
                    + "funciones, entrada_programada, salida_programada, entrada_real, salida_real, minutos_diferencia, minutos_retraso, "
                    + "minutos_programados, minutos_trabajados, salida_automatica, metodo_entrada, calculado_en) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", dias, 1000, (ps, d) -> {
                ps.setInt(1, d.empleadoId());
                ps.setDate(2, Date.valueOf(d.fecha()));
                ps.setObject(3, d.departamentoId());
                ps.setString(4, d.rol());
                ps.setString(5, d.estado());
                ps.setString(6, d.tipoDia());
                ps.setString(7, d.turno());
                ps.setString(8, d.funciones());
                ps.setTime(9, d.entradaProgramada() == null ? null : Time.valueOf(d.entradaProgramada()));
                ps.setTime(10, d.salidaProgramada() == null ? null : Time.valueOf(d.salidaProgramada()));
                ps.setTime(11, d.entradaReal() == null ? null : Time.valueOf(d.entradaReal()));
                ps.setTime(12, d.salidaReal() == null ? null : Time.valueOf(d.salidaReal()));
                ps.setObject(13, d.minutosDiferencia());
                ps.setObject(14, d.minutosRetraso());
                ps.setObject(15, d.minutosProgramados());
                ps.setObject(16, d.minutosTrabajados());
                ps.setBoolean(17, d.salidaAutomatica());
                ps.setString(18, d.metodoEntrada());
                ps.setTimestamp(19, ahora);
            });
        });
    }
}
