package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Departamento;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.DepartamentoRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.example.ControlNGR.service.ResumenAsistenciaService.*;

/**
 * Dashboard de asistencia, ficha del colaborador y "Mi récord", sobre el resumen diario.
 * Alcance: dirección, gerencia y jefaturas ven todos los equipos; supervisores y gestores solo el suyo;
 * cada colaborador, solo su propio récord y solo si el panel lo habilitó.
 */
@Service
public class ReporteAsistenciaService {

    /** Filtros del dashboard. personal: activos, cesados o todos. */
    public record Filtro(LocalDate desde, LocalDate hasta, Integer departamentoId, String rol, String turno,
                         String personal, Integer empleadoId) {}

    private static final int MAX_DIAS = 366 * 15;

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioActual usuarioActual;
    private final EmpleadoRepository empleadoRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ParametroService parametroService;

    public ReporteAsistenciaService(NamedParameterJdbcTemplate jdbc, UsuarioActual usuarioActual,
                                    EmpleadoRepository empleadoRepository, DepartamentoRepository departamentoRepository,
                                    ParametroService parametroService) {
        this.jdbc = jdbc;
        this.usuarioActual = usuarioActual;
        this.empleadoRepository = empleadoRepository;
        this.departamentoRepository = departamentoRepository;
        this.parametroService = parametroService;
    }

    // ==================== ALCANCE ====================

    /** Departamento al que se limita quien consulta, o null si ve todos (dirección, gerencia y jefaturas). */
    public Integer departamentoForzado(Usuario u) {
        if (Roles.esGerencia(u.getRol())) return null;
        if (!Roles.esGestion(u.getRol())) throw new AccesoDenegadoException("Solo las jefaturas consultan el dashboard de asistencia");
        Empleado propio = u.getEmpleado();
        if (propio == null || propio.getDepartamento() == null) {
            throw new AccesoDenegadoException("Su usuario no tiene equipo asignado: pida que lo asignen para ver el dashboard");
        }
        return propio.getDepartamento().getId();
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new IllegalArgumentException("Indique un período válido (desde ≤ hasta)");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > MAX_DIAS) {
            throw new IllegalArgumentException("El período no puede superar 15 años");
        }
    }

    // ==================== DASHBOARD ====================

    public Map<String, Object> dashboard(Filtro pedido) {
        Usuario u = usuarioActual.requerido();
        Integer forzado = departamentoForzado(u);
        Filtro f = forzado == null ? pedido
                : new Filtro(pedido.desde(), pedido.hasta(), forzado, pedido.rol(), pedido.turno(), pedido.personal(), pedido.empleadoId());
        validarRango(f.desde(), f.hasta());

        long dias = ChronoUnit.DAYS.between(f.desde(), f.hasta()) + 1;
        Filtro anterior = new Filtro(f.desde().minusDays(dias), f.desde().minusDays(1), f.departamentoId(), f.rol(),
                f.turno(), f.personal(), f.empleadoId());
        int tolerancia = Math.max(0, parametroService.entero(ParametroService.TOLERANCIA_TARDANZA_MINUTOS, 10));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", f.desde().toString());
        r.put("hasta", f.hasta().toString());
        r.put("anteriorDesde", anterior.desde().toString());
        r.put("anteriorHasta", anterior.hasta().toString());
        r.put("toleranciaMinutos", tolerancia);
        r.put("departamentoForzado", forzado);
        r.put("kpis", kpis(f));
        r.put("kpisAnterior", kpis(anterior));
        r.put("porMes", porMes(f));
        r.put("llegadas", llegadas(f, tolerancia));
        r.put("mapaTardanzas", mapaTardanzas(f));
        r.put("porEquipo", forzado == null && f.departamentoId() == null ? porEquipo(f) : List.of());
        r.put("colaboradores", colaboradores(f));
        r.put("actualizado", jdbc.getJdbcTemplate().queryForObject(
                "SELECT MAX(calculado_en) FROM asistencia_resumen_diario", String.class));
        return r;
    }

    /** WHERE común: período, equipo, rol, turno, personal activo/cesado y colaborador. */
    private String where(Filtro f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" FROM asistencia_resumen_diario r JOIN empleados e ON e.id = r.empleado_id"
                + " WHERE r.fecha BETWEEN :desde AND :hasta");
        p.addValue("desde", Date.valueOf(f.desde())).addValue("hasta", Date.valueOf(f.hasta()));
        if (f.departamentoId() != null) { w.append(" AND r.departamento_id = :depto"); p.addValue("depto", f.departamentoId()); }
        if (f.rol() != null && !f.rol().isBlank()) { w.append(" AND LOWER(r.rol) = :rol"); p.addValue("rol", f.rol().toLowerCase()); }
        if (f.turno() != null && !f.turno().isBlank()) { w.append(" AND r.turno = :turno"); p.addValue("turno", f.turno().toLowerCase()); }
        if ("activos".equals(f.personal())) w.append(" AND e.activo = 1");
        if ("cesados".equals(f.personal())) w.append(" AND e.activo = 0");
        if (f.empleadoId() != null) { w.append(" AND r.empleado_id = :emp"); p.addValue("emp", f.empleadoId()); }
        return w.toString();
    }

    private static final String CONTEOS =
            "SUM(r.estado = 'a_tiempo') a_tiempo, SUM(r.estado = 'tardanza') tardanza, SUM(r.estado = 'falta') falta, "
          + "SUM(r.estado = 'permiso') permiso, COALESCE(SUM(r.minutos_retraso), 0) min_retraso, "
          + "SUM(r.salida_automatica) salidas_auto, "
          + "COALESCE(SUM(CASE WHEN r.minutos_trabajados IS NOT NULL THEN r.minutos_trabajados END), 0) min_trab, "
          + "COALESCE(SUM(CASE WHEN r.minutos_trabajados IS NOT NULL THEN r.minutos_programados END), 0) min_prog, "
          + "AVG(r.minutos_diferencia) dif_prom";

    private Map<String, Object> kpis(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.query("SELECT " + CONTEOS + ", COUNT(DISTINCT r.empleado_id) personas" + where(f, p), p,
                rs -> rs.next() ? conteos(rs, true) : Map.of());
    }

    /** a tiempo, tardanzas, faltas, minutos, % puntualidad = a tiempo / días con entrada, % asistencia = con entrada / programados. */
    private static Map<String, Object> conteos(ResultSet rs, boolean conPersonas) throws SQLException {
        long aTiempo = rs.getLong("a_tiempo"), tardanza = rs.getLong("tardanza"), falta = rs.getLong("falta");
        long minRetraso = rs.getLong("min_retraso"), minTrab = rs.getLong("min_trab"), minProg = rs.getLong("min_prog");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aTiempo", aTiempo);
        m.put("tardanzas", tardanza);
        m.put("faltas", falta);
        m.put("permisos", rs.getLong("permiso"));
        m.put("programados", aTiempo + tardanza + falta);
        m.put("minutosRetraso", minRetraso);
        m.put("promedioRetraso", tardanza == 0 ? null : Math.round((double) minRetraso / tardanza));
        m.put("salidasAutomaticas", rs.getLong("salidas_auto"));
        m.put("puntualidad", porcentaje(aTiempo, aTiempo + tardanza));
        m.put("asistencia", porcentaje(aTiempo + tardanza, aTiempo + tardanza + falta));
        m.put("horas", porcentaje(minTrab, minProg));
        m.put("minutosTrabajados", minTrab);
        Object dif = rs.getObject("dif_prom");
        m.put("llegadaPromedio", dif == null ? null : Math.round(((Number) dif).doubleValue()));
        if (conPersonas) m.put("personas", rs.getLong("personas"));
        return m;
    }

    private static Double porcentaje(long parte, long total) {
        return total == 0 ? null : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private List<Map<String, Object>> porMes(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.query("SELECT DATE_FORMAT(r.fecha, '%Y-%m') mes, " + CONTEOS + where(f, p) + " GROUP BY mes ORDER BY mes", p,
                (rs, i) -> {
                    Map<String, Object> m = conteos(rs, false);
                    m.put("mes", rs.getString("mes"));
                    return m;
                });
    }

    /** Cuántas entradas caen en cada rango de minutos respecto al turno (la tolerancia separa a tiempo de tarde). */
    private List<Map<String, Object>> llegadas(Filtro f, int tolerancia) {
        // Límites superiores de cada rango (el último es abierto): ≤ -15, (-15,-5], (-5,0], (0,tol], (tol,tol+10], (tol+10,tol+20], > tol+20
        int[] limites = { -15, -5, 0, tolerancia, tolerancia + 10, tolerancia + 20 };
        long[] n = new long[limites.length + 1];
        MapSqlParameterSource p = new MapSqlParameterSource();
        jdbc.query("SELECT r.minutos_diferencia d, COUNT(*) n" + where(f, p) + " AND r.minutos_diferencia IS NOT NULL GROUP BY d", p, rs -> {
            n[rango(rs.getInt("d"), limites)] += rs.getLong("n");
        });
        List<Map<String, Object>> lista = new ArrayList<>();
        for (int i = 0; i < n.length; i++) {
            String etiqueta = i == 0 ? "≤ " + limites[0]
                    : i == limites.length ? "> +" + limites[i - 1]
                    : conSigno(limites[i - 1]) + " a " + conSigno(limites[i]);
            lista.add(Map.of("rango", etiqueta, "cantidad", n[i], "tarde", i > 3));
        }
        return lista;
    }

    static int rango(int minutos, int[] limites) {
        for (int i = 0; i < limites.length; i++) if (minutos <= limites[i]) return i;
        return limites.length;
    }

    private static String conSigno(int m) {
        return m > 0 ? "+" + m : String.valueOf(m);
    }

    /** Tardanzas por día de la semana (1 = lunes) y hora de entrada programada, con su porcentaje. */
    private List<Map<String, Object>> mapaTardanzas(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.query("SELECT WEEKDAY(r.fecha) + 1 dia, HOUR(r.entrada_programada) hora, SUM(r.estado = 'tardanza') tardanzas, "
                + "SUM(r.estado IN ('a_tiempo', 'tardanza')) entradas" + where(f, p)
                + " AND r.entrada_programada IS NOT NULL GROUP BY dia, hora ORDER BY hora, dia", p, (rs, i) -> Map.of(
                "dia", rs.getInt("dia"), "hora", rs.getInt("hora"),
                "tardanzas", rs.getLong("tardanzas"), "entradas", rs.getLong("entradas")));
    }

    private List<Map<String, Object>> porEquipo(Filtro f) {
        Map<Integer, String> nombres = new HashMap<>();
        for (Departamento d : departamentoRepository.findAll()) nombres.put(d.getId(), d.getNombre());
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.query("SELECT r.departamento_id depto, " + CONTEOS + where(f, p) + " GROUP BY depto", p, (rs, i) -> {
            Map<String, Object> m = conteos(rs, false);
            Object depto = rs.getObject("depto");
            m.put("departamentoId", depto);
            m.put("nombre", depto == null ? "Sin equipo" : nombres.getOrDefault(((Number) depto).intValue(), "Equipo " + depto));
            return m;
        }).stream().sorted(Comparator.comparing(m -> -Optional.ofNullable((Double) m.get("puntualidad")).orElse(-1.0))).toList();
    }

    /** Tabla de colaboradores; la tendencia compara su puntualidad de los últimos 30 días del período con el resto. */
    private List<Map<String, Object>> colaboradores(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        LocalDate corte = f.hasta().minusDays(29);
        p.addValue("corte", Date.valueOf(corte));
        String sql = "SELECT r.empleado_id id, e.nombre, e.ingreso, e.activo, MAX(r.departamento_id) depto, " + CONTEOS
                + ", SUM(r.fecha >= :corte AND r.estado = 'a_tiempo') rec_ok, SUM(r.fecha >= :corte AND r.estado IN ('a_tiempo','tardanza')) rec_tot"
                + ", SUM(r.fecha < :corte AND r.estado = 'a_tiempo') ant_ok, SUM(r.fecha < :corte AND r.estado IN ('a_tiempo','tardanza')) ant_tot"
                + where(f, p) + " GROUP BY r.empleado_id, e.nombre, e.ingreso, e.activo ORDER BY e.nombre";
        Map<Integer, String> nombres = new HashMap<>();
        for (Departamento d : departamentoRepository.findAll()) nombres.put(d.getId(), d.getNombre());
        return jdbc.query(sql, p, (rs, i) -> {
            Map<String, Object> m = conteos(rs, false);
            m.put("empleadoId", rs.getInt("id"));
            m.put("nombre", rs.getString("nombre"));
            Date ingreso = rs.getDate("ingreso");
            m.put("ingreso", ingreso == null ? null : ingreso.toLocalDate().toString());
            m.put("activo", rs.getBoolean("activo"));
            Object depto = rs.getObject("depto");
            m.put("departamento", depto == null ? null : nombres.get(((Number) depto).intValue()));
            Double reciente = porcentaje(rs.getLong("rec_ok"), rs.getLong("rec_tot"));
            Double previa = porcentaje(rs.getLong("ant_ok"), rs.getLong("ant_tot"));
            m.put("tendencia", reciente == null || previa == null ? null : Math.round((reciente - previa) * 10) / 10.0);
            return m;
        });
    }

    // ==================== FICHA DEL COLABORADOR ====================

    /** Ficha de un colaborador para jefaturas (dentro de su alcance). */
    public Map<String, Object> ficha(Integer empleadoId, LocalDate desde, LocalDate hasta) {
        Usuario u = usuarioActual.requerido();
        Integer forzado = departamentoForzado(u);
        Empleado e = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Colaborador no encontrado"));
        if (forzado != null && (e.getDepartamento() == null || !forzado.equals(e.getDepartamento().getId()))) {
            throw new AccesoDenegadoException("Solo puede consultar a los colaboradores de su equipo");
        }
        LocalDate inicio = desde != null ? desde : (e.getIngreso() != null ? e.getIngreso() : primeraFecha(empleadoId));
        return armarFicha(e, inicio, hasta != null ? hasta : LocalDate.now());
    }

    /** Récord propio del colaborador: solo si el panel lo habilitó, desde la fecha configurada. */
    public Map<String, Object> miRecord() {
        if (!miRecordHabilitado()) {
            throw new AccesoDenegadoException("La consulta de su récord de asistencia aún no está habilitada");
        }
        Empleado e = usuarioActual.empleadoRequerido();
        LocalDate desde = miRecordDesde(e);
        Map<String, Object> ficha = armarFicha(e, desde, LocalDate.now());
        ficha.put("propio", true);
        return ficha;
    }

    public boolean miRecordHabilitado() {
        return parametroService.booleano(ParametroService.MI_RECORD_HABILITADO, false);
    }

    /** INGRESO → su fecha de ingreso (o su primer día registrado); una fecha → la mayor entre esa y su ingreso. */
    LocalDate miRecordDesde(Empleado e) {
        LocalDate ingreso = e.getIngreso() != null ? e.getIngreso() : primeraFecha(e.getId());
        String valor = parametroService.texto(ParametroService.MI_RECORD_DESDE, "INGRESO").trim();
        if ("INGRESO".equalsIgnoreCase(valor)) return ingreso;
        try {
            LocalDate fecha = LocalDate.parse(valor);
            return ingreso != null && ingreso.isAfter(fecha) ? ingreso : fecha;
        } catch (Exception ex) {
            return ingreso;
        }
    }

    private LocalDate primeraFecha(Integer empleadoId) {
        LocalDate f = jdbc.getJdbcTemplate().queryForObject(
                "SELECT MIN(fecha) FROM asistencia_resumen_diario WHERE empleado_id = ?", LocalDate.class, empleadoId);
        return f != null ? f : LocalDate.now().withDayOfMonth(1);
    }

    private Map<String, Object> armarFicha(Empleado e, LocalDate desde, LocalDate hasta) {
        if (desde == null) desde = hasta.withDayOfMonth(1);
        if (desde.isAfter(hasta)) desde = hasta;
        validarRango(desde, hasta);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("emp", e.getId()).addValue("desde", Date.valueOf(desde)).addValue("hasta", Date.valueOf(hasta));
        List<Map<String, Object>> filas = jdbc.query("SELECT fecha, estado, turno, funciones, entrada_programada, salida_programada, "
                + "entrada_real, salida_real, minutos_diferencia, minutos_retraso, salida_automatica FROM asistencia_resumen_diario "
                + "WHERE empleado_id = :emp AND fecha BETWEEN :desde AND :hasta ORDER BY fecha", p, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fecha", rs.getDate("fecha").toLocalDate().toString());
            m.put("estado", rs.getString("estado"));
            m.put("turno", rs.getString("turno"));
            m.put("funciones", FuncionTurnoService.aLista(rs.getString("funciones")));
            m.put("entradaProgramada", hhmm(rs.getTime("entrada_programada")));
            m.put("salidaProgramada", hhmm(rs.getTime("salida_programada")));
            m.put("entrada", hhmm(rs.getTime("entrada_real")));
            m.put("salida", hhmm(rs.getTime("salida_real")));
            m.put("minutosDiferencia", rs.getObject("minutos_diferencia"));
            m.put("minutosRetraso", rs.getObject("minutos_retraso"));
            m.put("salidaAutomatica", rs.getBoolean("salida_automatica"));
            return m;
        });

        long aTiempo = 0, tardanzas = 0, faltas = 0, minRetraso = 0, salidasAuto = 0, difSuma = 0, difN = 0;
        String ultimaFalta = null;
        Map<String, Long> ausencias = new LinkedHashMap<>();
        Map<String, long[]> porMes = new TreeMap<>();
        List<Map<String, String>> calendario = new ArrayList<>();
        for (Map<String, Object> f : filas) {
            String estado = (String) f.get("estado");
            String fecha = (String) f.get("fecha");
            calendario.add(Map.of("fecha", fecha, "estado", estado));
            long[] mes = porMes.computeIfAbsent(fecha.substring(0, 7), k -> new long[3]);
            switch (estado) {
                case A_TIEMPO -> { aTiempo++; mes[0]++; }
                case TARDANZA -> { tardanzas++; mes[1]++; minRetraso += ((Number) f.get("minutosRetraso")).longValue(); }
                case FALTA -> { faltas++; mes[2]++; ultimaFalta = fecha; }
                case "vacaciones", "descanso_medico", "licencia", "compensado", PERMISO -> ausencias.merge(estado, 1L, Long::sum);
                default -> { }
            }
            if (Boolean.TRUE.equals(f.get("salidaAutomatica"))) salidasAuto++;
            if (f.get("minutosDiferencia") != null) { difSuma += ((Number) f.get("minutosDiferencia")).longValue(); difN++; }
        }
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("programados", aTiempo + tardanzas + faltas);
        kpis.put("aTiempo", aTiempo);
        kpis.put("tardanzas", tardanzas);
        kpis.put("faltas", faltas);
        kpis.put("minutosRetraso", minRetraso);
        kpis.put("promedioRetraso", tardanzas == 0 ? null : Math.round((double) minRetraso / tardanzas));
        kpis.put("puntualidad", porcentaje(aTiempo, aTiempo + tardanzas));
        kpis.put("asistencia", porcentaje(aTiempo + tardanzas, aTiempo + tardanzas + faltas));
        kpis.put("llegadaPromedio", difN == 0 ? null : Math.round((double) difSuma / difN));
        kpis.put("salidasAutomaticas", salidasAuto);
        kpis.put("ultimaFalta", ultimaFalta);
        kpis.put("ausencias", ausencias);

        List<Map<String, Object>> meses = new ArrayList<>();
        porMes.forEach((mes, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mes", mes);
            m.put("aTiempo", v[0]);
            m.put("tardanzas", v[1]);
            m.put("faltas", v[2]);
            m.put("puntualidad", porcentaje(v[0], v[0] + v[1]));
            meses.add(m);
        });
        List<Map<String, Object>> ultimas = new ArrayList<>();
        for (int i = filas.size() - 1; i >= 0 && ultimas.size() < 15; i--) {
            Map<String, Object> f = filas.get(i);
            if (f.get("entradaProgramada") != null || f.get("entrada") != null) ultimas.add(f);
        }

        Map<String, Object> empleado = new LinkedHashMap<>();
        empleado.put("id", e.getId());
        empleado.put("nombre", e.getNombre());
        empleado.put("cargo", e.getCargo());
        empleado.put("departamento", e.getDepartamentoNombre());
        empleado.put("ingreso", e.getIngreso() == null ? null : e.getIngreso().toString());
        empleado.put("activo", e.getActivo());
        empleado.put("foto", e.getFoto());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("empleado", empleado);
        r.put("desde", desde.toString());
        r.put("hasta", hasta.toString());
        r.put("kpis", kpis);
        r.put("porMes", meses);
        r.put("calendario", calendario);
        r.put("ultimas", ultimas);
        return r;
    }

    private static String hhmm(java.sql.Time t) {
        if (t == null) return null;
        LocalTime l = t.toLocalTime();
        return String.format("%02d:%02d", l.getHour(), l.getMinute());
    }
}
