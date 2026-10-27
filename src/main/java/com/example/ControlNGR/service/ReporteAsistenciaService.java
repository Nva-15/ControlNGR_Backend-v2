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
 * Alcance por rol (el rol actual del colaborador): dirección, gerencia y jefaturas ven a todos; supervisores,
 * al personal operativo (técnico, HD, NOC y BO) de todas las áreas; gestores, a los asistentes. Cada
 * colaborador ve solo su propio récord y solo si el panel lo habilitó.
 */
@Service
public class ReporteAsistenciaService {

    /** Filtros del dashboard. personal: activos, cesados o todos. alcance: roles visibles (lo fija el servicio). */
    public record Filtro(LocalDate desde, LocalDate hasta, Integer departamentoId, String rol, String turno, String funcion,
                         String personal, Integer empleadoId, Set<String> alcance) {

        Filtro conAlcance(Set<String> roles) {
            return new Filtro(desde, hasta, departamentoId, rol, turno, funcion, personal, empleadoId, roles);
        }

        Filtro conPeriodo(LocalDate a, LocalDate b) {
            return new Filtro(a, b, departamentoId, rol, turno, funcion, personal, empleadoId, alcance);
        }
    }

    private static final int MAX_DIAS = 366 * 15;
    /** Minutos sobre lo programado a partir de los cuales el día suma horas extra. */
    static final int MINUTOS_MINIMOS_EXTRA = 15;
    /** Hasta estos días la serie va por día; hasta {@link #DIAS_POR_SEMANA}, por semana; más, por mes. */
    static final int DIAS_POR_DIA = 62;
    static final int DIAS_POR_SEMANA = 190;

    private static final Set<String> AUSENCIAS = Set.of("vacaciones", "descanso_medico", "licencia", "compensado", PERMISO);
    private static final Set<String> DESCANSOS = Set.of("descanso", "descanso_feriado");
    /** Orden de la tabla de hoy: primero lo que requiere atención. */
    private static final List<String> URGENCIA = List.of(FALTA, TARDANZA, PENDIENTE, A_TIEMPO, ASISTIO, PERMISO);

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioActual usuarioActual;
    private final EmpleadoRepository empleadoRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ParametroService parametroService;
    private final ResumenAsistenciaService resumenService;

    public ReporteAsistenciaService(NamedParameterJdbcTemplate jdbc, UsuarioActual usuarioActual,
                                    EmpleadoRepository empleadoRepository, DepartamentoRepository departamentoRepository,
                                    ParametroService parametroService, ResumenAsistenciaService resumenService) {
        this.jdbc = jdbc;
        this.usuarioActual = usuarioActual;
        this.empleadoRepository = empleadoRepository;
        this.departamentoRepository = departamentoRepository;
        this.parametroService = parametroService;
        this.resumenService = resumenService;
    }

    // ==================== ALCANCE ====================

    /** Roles que ve quien consulta, o null si ve a todos (dirección, gerencia y jefaturas). */
    public Set<String> rolesVisibles(Usuario u) {
        String rol = u.getRol();
        if (Roles.esGerencia(rol)) return null;
        if (Roles.SUPERVISOR.equalsIgnoreCase(rol)) return Roles.OPERATIVOS;
        if (Roles.GESTOR.equalsIgnoreCase(rol)) return Set.of(Roles.ASISTENTE);
        throw new AccesoDenegadoException("Solo las jefaturas consultan el dashboard de asistencia");
    }

    static boolean dentroDelAlcance(Set<String> roles, String rolColaborador) {
        return roles == null || (rolColaborador != null && roles.contains(rolColaborador.toLowerCase()));
    }

    /** Condición SQL del alcance sobre el rol actual del colaborador (alias {@code e} de empleados); vacía si ve a todos. */
    public static String condicionAlcance(Set<String> roles, MapSqlParameterSource p) {
        if (roles == null) return "";
        p.addValue("alcance", roles);
        return " AND EXISTS (SELECT 1 FROM usuarios us JOIN tipos_usuario tu ON tu.id = us.tipo_usuario_id"
                + " WHERE us.empleado_id = e.id AND LOWER(tu.codigo) IN (:alcance))";
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
        Set<String> alcance = rolesVisibles(u);
        Filtro f = pedido.conAlcance(alcance);
        validarRango(f.desde(), f.hasta());

        LocalDate hoy = LocalDate.now();
        boolean soloHoy = f.desde().equals(hoy) && f.hasta().equals(hoy);
        if (soloHoy) resumenService.refrescarHoy();

        long dias = ChronoUnit.DAYS.between(f.desde(), f.hasta()) + 1;
        Filtro anterior = f.conPeriodo(f.desde().minusDays(dias), f.desde().minusDays(1));
        int tolerancia = Math.max(0, parametroService.entero(ParametroService.TOLERANCIA_TARDANZA_MINUTOS, 10));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", f.desde().toString());
        r.put("hasta", f.hasta().toString());
        r.put("anteriorDesde", anterior.desde().toString());
        r.put("anteriorHasta", anterior.hasta().toString());
        r.put("toleranciaMinutos", tolerancia);
        r.put("alcanceRoles", alcance == null ? null : alcance.stream().sorted().toList());
        r.put("kpis", kpis(f));
        r.put("kpisAnterior", kpis(anterior));
        if (soloHoy) r.put("hoy", hoy(f));
        r.put("serie", serie(f, dias));
        r.put("llegadas", llegadas(f, tolerancia));
        r.put("mapaTardanzas", mapaTardanzas(f));
        r.put("porEquipo", f.departamentoId() == null ? porEquipo(f) : List.of());
        r.put("porFuncion", porFuncion(f));
        r.put("colaboradores", colaboradores(f));
        r.put("semanasBorrador", semanasBorrador(f.desde(), f.hasta()));
        r.put("actualizado", jdbc.getJdbcTemplate().queryForObject(
                "SELECT MAX(calculado_en) FROM asistencia_resumen_diario", String.class));
        return r;
    }

    /** WHERE común: período, alcance, equipo, rol, turno, función, personal activo/cesado y colaborador. */
    private String where(Filtro f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" FROM asistencia_resumen_diario r JOIN empleados e ON e.id = r.empleado_id"
                + " WHERE r.fecha BETWEEN :desde AND :hasta");
        p.addValue("desde", Date.valueOf(f.desde())).addValue("hasta", Date.valueOf(f.hasta()));
        w.append(condicionAlcance(f.alcance(), p));
        if (f.departamentoId() != null) { w.append(" AND r.departamento_id = :depto"); p.addValue("depto", f.departamentoId()); }
        if (f.rol() != null && !f.rol().isBlank()) { w.append(" AND LOWER(r.rol) = :rol"); p.addValue("rol", f.rol().toLowerCase()); }
        if (f.turno() != null && !f.turno().isBlank()) { w.append(" AND r.turno = :turno"); p.addValue("turno", f.turno().toLowerCase()); }
        if (f.funcion() != null && !f.funcion().isBlank()) {
            w.append(" AND FIND_IN_SET(:funcion, r.funciones) > 0");
            p.addValue("funcion", f.funcion().trim().toUpperCase());
        }
        if ("activos".equals(f.personal())) w.append(" AND e.activo = 1");
        if ("cesados".equals(f.personal())) w.append(" AND e.activo = 0");
        if (f.empleadoId() != null) { w.append(" AND r.empleado_id = :emp"); p.addValue("emp", f.empleadoId()); }
        return w.toString();
    }

    /** Conteos y promedios. Las horas (y extras) solo de días con salida marcada; la salida promedio, sin las automáticas. */
    private static final String CONTEOS =
            "SUM(r.estado = 'a_tiempo') a_tiempo, SUM(r.estado = 'tardanza') tardanza, SUM(r.estado = 'falta') falta, "
          + "SUM(r.estado = 'permiso') permiso, SUM(r.estado = 'asistio') sin_horario, SUM(r.entrada_real IS NOT NULL) marcados, "
          + "COALESCE(SUM(r.minutos_retraso), 0) min_retraso, SUM(r.salida_automatica) salidas_auto, "
          + "COALESCE(SUM(CASE WHEN r.minutos_trabajados IS NOT NULL AND r.minutos_programados IS NOT NULL THEN r.minutos_trabajados END), 0) min_trab, "
          + "COALESCE(SUM(CASE WHEN r.minutos_trabajados IS NOT NULL AND r.minutos_programados IS NOT NULL THEN r.minutos_programados END), 0) min_prog, "
          + "COALESCE(SUM(CASE WHEN r.minutos_trabajados - r.minutos_programados >= " + MINUTOS_MINIMOS_EXTRA
          + " THEN r.minutos_trabajados - r.minutos_programados END), 0) min_extra, "
          + "AVG(r.minutos_trabajados) prom_trab, "
          // Hora promedio como promedio circular (una salida a las 23:50 y otra a las 00:10 promedian 00:00, no 12:00)
          + "AVG(SIN(TIME_TO_SEC(r.entrada_real) * PI() / 43200)) e_sin, AVG(COS(TIME_TO_SEC(r.entrada_real) * PI() / 43200)) e_cos, "
          + "AVG(CASE WHEN r.salida_automatica = 0 THEN SIN(TIME_TO_SEC(r.salida_real) * PI() / 43200) END) s_sin, "
          + "AVG(CASE WHEN r.salida_automatica = 0 THEN COS(TIME_TO_SEC(r.salida_real) * PI() / 43200) END) s_cos, "
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
        m.put("diasMarcados", rs.getLong("marcados"));
        m.put("diasSinHorario", rs.getLong("sin_horario"));
        m.put("minutosRetraso", minRetraso);
        m.put("promedioRetraso", tardanza == 0 ? null : Math.round((double) minRetraso / tardanza));
        m.put("salidasAutomaticas", rs.getLong("salidas_auto"));
        m.put("puntualidad", porcentaje(aTiempo, aTiempo + tardanza));
        m.put("asistencia", porcentaje(aTiempo + tardanza, aTiempo + tardanza + falta));
        m.put("horas", porcentaje(minTrab, minProg));
        m.put("minutosTrabajados", minTrab);
        m.put("minutosExtra", rs.getLong("min_extra"));
        m.put("minutosPorDia", redondeo(rs.getObject("prom_trab")));
        m.put("entradaPromedio", horaPromedio(rs.getObject("e_sin"), rs.getObject("e_cos")));
        m.put("salidaPromedio", horaPromedio(rs.getObject("s_sin"), rs.getObject("s_cos")));
        m.put("llegadaPromedio", redondeo(rs.getObject("dif_prom")));
        if (conPersonas) m.put("personas", rs.getLong("personas"));
        return m;
    }

    private static Long redondeo(Object valor) {
        return valor == null ? null : Math.round(((Number) valor).doubleValue());
    }

    /** Hora "HH:mm" a partir del promedio de seno y coseno de las horas (promedio circular); null si no hay datos o no hay una hora típica. */
    static String horaPromedio(Object seno, Object coseno) {
        if (seno == null || coseno == null) return null;
        double y = ((Number) seno).doubleValue(), x = ((Number) coseno).doubleValue();
        if (x * x + y * y < 1e-6) return null;
        double angulo = Math.atan2(y, x);
        if (angulo < 0) angulo += 2 * Math.PI;
        return horaDeSegundos(angulo * 43200 / Math.PI);
    }

    /** Segundos del día (promedio) a "HH:mm". */
    static String horaDeSegundos(Object segundos) {
        if (segundos == null) return null;
        long s = Math.round(((Number) segundos).doubleValue());
        long minutos = Math.round(s / 60.0) % (24 * 60);
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    private static Double porcentaje(long parte, long total) {
        return total == 0 ? null : Math.round(parte * 1000.0 / total) / 10.0;
    }

    /** Granularidad de la serie según la duración del período. */
    static String granularidad(long dias) {
        return dias <= DIAS_POR_DIA ? "dia" : dias <= DIAS_POR_SEMANA ? "semana" : "mes";
    }

    /** Puntualidad y conteos en el tiempo: por día, por semana (clave = su lunes) o por mes. */
    private Map<String, Object> serie(Filtro f, long dias) {
        String g = granularidad(dias);
        String clave = switch (g) {
            case "dia" -> "DATE_FORMAT(r.fecha, '%Y-%m-%d')";
            case "semana" -> "DATE_FORMAT(DATE_SUB(r.fecha, INTERVAL WEEKDAY(r.fecha) DAY), '%Y-%m-%d')";
            default -> "DATE_FORMAT(r.fecha, '%Y-%m')";
        };
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<Map<String, Object>> puntos = jdbc.query("SELECT " + clave + " clave, " + CONTEOS + where(f, p)
                + " GROUP BY clave ORDER BY clave", p, (rs, i) -> {
            Map<String, Object> m = conteos(rs, false);
            m.put("clave", rs.getString("clave"));
            return m;
        });
        return Map.of("granularidad", g, "puntos", puntos);
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

    private Map<Integer, String> nombresEquipos() {
        Map<Integer, String> nombres = new HashMap<>();
        for (Departamento d : departamentoRepository.findAll()) nombres.put(d.getId(), d.getNombre());
        return nombres;
    }

    private static String nombreEquipo(Map<Integer, String> nombres, Object depto) {
        return depto == null ? "Sin equipo" : nombres.getOrDefault(((Number) depto).intValue(), "Equipo " + depto);
    }

    private List<Map<String, Object>> porEquipo(Filtro f) {
        Map<Integer, String> nombres = nombresEquipos();
        MapSqlParameterSource p = new MapSqlParameterSource();
        return jdbc.query("SELECT r.departamento_id depto, " + CONTEOS + where(f, p) + " GROUP BY depto", p, (rs, i) -> {
            Map<String, Object> m = conteos(rs, false);
            Object depto = rs.getObject("depto");
            m.put("departamentoId", depto);
            m.put("nombre", nombreEquipo(nombres, depto));
            return m;
        }).stream().sorted(Comparator.comparing(m -> -Optional.ofNullable((Double) m.get("puntualidad")).orElse(-1.0))).toList();
    }

    /** Comparativo por función del turno (un día con "SP,PRE" cuenta para ambas). */
    private List<Map<String, Object>> porFuncion(Filtro f) {
        Map<String, long[]> acumulado = new TreeMap<>();
        MapSqlParameterSource p = new MapSqlParameterSource();
        jdbc.query("SELECT r.funciones fn, SUM(r.estado = 'a_tiempo') a_tiempo, SUM(r.estado = 'tardanza') tardanza, "
                + "SUM(r.estado = 'falta') falta, COALESCE(SUM(r.minutos_retraso), 0) min_retraso" + where(f, p)
                + " AND r.funciones IS NOT NULL AND r.funciones <> '' GROUP BY fn", p, rs -> {
            for (String codigo : FuncionTurnoService.aLista(rs.getString("fn"))) {
                long[] v = acumulado.computeIfAbsent(codigo, k -> new long[4]);
                v[0] += rs.getLong("a_tiempo");
                v[1] += rs.getLong("tardanza");
                v[2] += rs.getLong("falta");
                v[3] += rs.getLong("min_retraso");
            }
        });
        List<Map<String, Object>> lista = new ArrayList<>();
        acumulado.forEach((codigo, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("funcion", codigo);
            m.put("aTiempo", v[0]);
            m.put("tardanzas", v[1]);
            m.put("faltas", v[2]);
            m.put("programados", v[0] + v[1] + v[2]);
            m.put("minutosRetraso", v[3]);
            m.put("puntualidad", porcentaje(v[0], v[0] + v[1]));
            m.put("asistencia", porcentaje(v[0] + v[1], v[0] + v[1] + v[2]));
            lista.add(m);
        });
        return lista;
    }

    /** Tabla de colaboradores; la tendencia compara su puntualidad de los últimos 30 días del período con el resto. */
    private List<Map<String, Object>> colaboradores(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        LocalDate corte = f.hasta().minusDays(29);
        p.addValue("corte", Date.valueOf(corte));
        String sql = "SELECT r.empleado_id id, e.nombre, e.ingreso, e.activo, MAX(r.departamento_id) depto, MAX(r.rol) rol, " + CONTEOS
                + ", SUM(r.fecha >= :corte AND r.estado = 'a_tiempo') rec_ok, SUM(r.fecha >= :corte AND r.estado IN ('a_tiempo','tardanza')) rec_tot"
                + ", SUM(r.fecha < :corte AND r.estado = 'a_tiempo') ant_ok, SUM(r.fecha < :corte AND r.estado IN ('a_tiempo','tardanza')) ant_tot"
                + where(f, p) + " GROUP BY r.empleado_id, e.nombre, e.ingreso, e.activo ORDER BY e.nombre";
        Map<Integer, String> nombres = nombresEquipos();
        return jdbc.query(sql, p, (rs, i) -> {
            Map<String, Object> m = conteos(rs, false);
            m.put("empleadoId", rs.getInt("id"));
            m.put("nombre", rs.getString("nombre"));
            Date ingreso = rs.getDate("ingreso");
            m.put("ingreso", ingreso == null ? null : ingreso.toLocalDate().toString());
            m.put("activo", rs.getBoolean("activo"));
            m.put("rol", rs.getString("rol"));
            Object depto = rs.getObject("depto");
            m.put("departamento", depto == null ? null : nombres.get(((Number) depto).intValue()));
            Double reciente = porcentaje(rs.getLong("rec_ok"), rs.getLong("rec_tot"));
            Double previa = porcentaje(rs.getLong("ant_ok"), rs.getLong("ant_tot"));
            m.put("tendencia", reciente == null || previa == null ? null : Math.round((reciente - previa) * 10) / 10.0);
            return m;
        });
    }

    // ==================== HOY EN VIVO ====================

    /** Estado de hoy: conteos, cada colaborador (primero lo urgente) y cada equipo. */
    private Map<String, Object> hoy(Filtro f) {
        Map<Integer, String> nombres = nombresEquipos();
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<Map<String, Object>> personas = jdbc.query("SELECT r.empleado_id, e.nombre, r.departamento_id, r.rol, r.estado, r.turno, "
                + "r.funciones, r.entrada_programada, r.salida_programada, r.entrada_real, r.salida_real, r.minutos_diferencia, "
                + "r.salida_automatica" + where(f, p), p, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("empleadoId", rs.getInt("empleado_id"));
            m.put("nombre", rs.getString("nombre"));
            Object depto = rs.getObject("departamento_id");
            m.put("departamentoId", depto);
            m.put("departamento", nombreEquipo(nombres, depto));
            m.put("rol", rs.getString("rol"));
            m.put("estado", rs.getString("estado"));
            m.put("turno", rs.getString("turno"));
            m.put("funciones", FuncionTurnoService.aLista(rs.getString("funciones")));
            m.put("entradaProgramada", hhmm(rs.getTime("entrada_programada")));
            m.put("salidaProgramada", hhmm(rs.getTime("salida_programada")));
            m.put("entrada", hhmm(rs.getTime("entrada_real")));
            m.put("salida", hhmm(rs.getTime("salida_real")));
            m.put("minutosDiferencia", rs.getObject("minutos_diferencia"));
            m.put("salidaAutomatica", rs.getBoolean("salida_automatica"));
            m.put("enTurno", rs.getTime("entrada_real") != null && rs.getTime("salida_real") == null);
            return m;
        });
        personas.sort(Comparator.comparingInt((Map<String, Object> m) -> urgencia((String) m.get("estado")))
                .thenComparing(m -> -Optional.ofNullable((Number) m.get("minutosDiferencia")).map(Number::intValue).orElse(0))
                .thenComparing(m -> Objects.toString(m.get("entradaProgramada"), "99:99"))
                .thenComparing(m -> (String) m.get("nombre")));

        Map<String, Object> kpis = contarHoy(personas);
        Map<Object, List<Map<String, Object>>> porDepto = new LinkedHashMap<>();
        for (Map<String, Object> m : personas) porDepto.computeIfAbsent(m.get("departamentoId"), k -> new ArrayList<>()).add(m);
        List<Map<String, Object>> equipos = new ArrayList<>();
        porDepto.forEach((depto, lista) -> {
            Map<String, Object> e = contarHoy(lista);
            e.put("departamentoId", depto);
            e.put("nombre", nombreEquipo(nombres, depto));
            equipos.add(e);
        });
        equipos.sort(Comparator.comparing(e -> (String) e.get("nombre")));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("kpis", kpis);
        r.put("colaboradores", personas);
        r.put("porEquipo", equipos);
        return r;
    }

    static int urgencia(String estado) {
        int i = URGENCIA.indexOf(estado);
        if (i >= 0) return i;
        return AUSENCIAS.contains(estado) ? URGENCIA.size() : URGENCIA.size() + 1;
    }

    /** Conteos de hoy: programados (con turno laboral), a tiempo, tardanzas, faltas, por llegar, en turno, ausencias y descansos. */
    static Map<String, Object> contarHoy(List<Map<String, Object>> personas) {
        long aTiempo = 0, tardanzas = 0, faltas = 0, porLlegar = 0, enTurno = 0, ausencias = 0, descansos = 0, sinHorario = 0;
        for (Map<String, Object> m : personas) {
            String estado = (String) m.get("estado");
            switch (estado) {
                case A_TIEMPO -> aTiempo++;
                case TARDANZA -> tardanzas++;
                case FALTA -> faltas++;
                case PENDIENTE -> porLlegar++;
                case ASISTIO -> sinHorario++;
                default -> {
                    if (AUSENCIAS.contains(estado)) ausencias++;
                    else if (DESCANSOS.contains(estado)) descansos++;
                }
            }
            if (Boolean.TRUE.equals(m.get("enTurno"))) enTurno++;
        }
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("programados", aTiempo + tardanzas + faltas + porLlegar);
        k.put("aTiempo", aTiempo);
        k.put("tardanzas", tardanzas);
        k.put("faltas", faltas);
        k.put("porLlegar", porLlegar);
        k.put("enTurno", enTurno);
        k.put("ausencias", ausencias);
        k.put("descansos", descansos);
        k.put("sinHorario", sinHorario);
        k.put("puntualidad", porcentaje(aTiempo, aTiempo + tardanzas));
        return k;
    }

    // ==================== AVISOS ====================

    /** Semanas en borrador que tocan el período (o los próximos 7 días): sus turnos no cuentan hasta publicarlas. */
    private List<Map<String, Object>> semanasBorrador(LocalDate desde, LocalDate hasta) {
        LocalDate limite = LocalDate.now().plusDays(7);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", Date.valueOf(desde)).addValue("hasta", Date.valueOf(hasta.isAfter(limite) ? hasta : limite));
        return jdbc.query("SELECT id, nombre, fecha_inicio, fecha_fin FROM horarios_semanales WHERE estado = 'borrador' "
                + "AND fecha_fin >= :desde AND fecha_inicio <= :hasta ORDER BY fecha_inicio", p, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getInt("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("fechaInicio", rs.getDate("fecha_inicio").toLocalDate().toString());
            m.put("fechaFin", rs.getDate("fecha_fin").toLocalDate().toString());
            return m;
        });
    }

    // ==================== FICHA DEL COLABORADOR ====================

    /** Ficha de un colaborador para jefaturas (dentro de su alcance). */
    public Map<String, Object> ficha(Integer empleadoId, LocalDate desde, LocalDate hasta) {
        Usuario u = usuarioActual.requerido();
        Set<String> alcance = rolesVisibles(u);
        Empleado e = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Colaborador no encontrado"));
        if (!dentroDelAlcance(alcance, e.getRol())) {
            throw new AccesoDenegadoException("Solo puede consultar a los colaboradores a su cargo");
        }
        LocalDate inicio = desde != null ? desde : inicioHistorial(e);
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

    /**
     * INGRESO → desde su ingreso (o antes, si marcó antes de esa fecha); una fecha → la mayor entre esa y
     * el inicio de su historial.
     */
    LocalDate miRecordDesde(Empleado e) {
        LocalDate inicio = inicioHistorial(e);
        String valor = parametroService.texto(ParametroService.MI_RECORD_DESDE, "INGRESO").trim();
        if ("INGRESO".equalsIgnoreCase(valor)) return inicio;
        try {
            LocalDate fecha = LocalDate.parse(valor);
            return inicio != null && inicio.isAfter(fecha) ? inicio : fecha;
        } catch (Exception ex) {
            return inicio;
        }
    }

    /** Inicio del historial: su ingreso o su primer día registrado, lo que sea antes (una fecha de ingreso mal registrada no oculta marcaciones). */
    LocalDate inicioHistorial(Empleado e) {
        LocalDate primera = jdbc.getJdbcTemplate().queryForObject(
                "SELECT MIN(fecha) FROM asistencia_resumen_diario WHERE empleado_id = ?", LocalDate.class, e.getId());
        LocalDate ingreso = e.getIngreso();
        if (ingreso == null) return primera != null ? primera : LocalDate.now().withDayOfMonth(1);
        return primera != null && primera.isBefore(ingreso) ? primera : ingreso;
    }

    private Map<String, Object> armarFicha(Empleado e, LocalDate desde, LocalDate hasta) {
        if (desde == null) desde = hasta.withDayOfMonth(1);
        if (desde.isAfter(hasta)) desde = hasta;
        validarRango(desde, hasta);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("emp", e.getId()).addValue("desde", Date.valueOf(desde)).addValue("hasta", Date.valueOf(hasta));
        List<Map<String, Object>> filas = jdbc.query("SELECT fecha, estado, turno, funciones, entrada_programada, salida_programada, "
                + "entrada_real, salida_real, minutos_diferencia, minutos_retraso, minutos_trabajados, salida_automatica FROM asistencia_resumen_diario "
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
            m.put("minutosTrabajados", rs.getObject("minutos_trabajados"));
            return m;
        });

        // Indicadores que también sirven sin horario: días marcados, horas promedio de entrada y salida, horas por día
        long marcados = 0, sinHorario = 0, nSalida = 0, minTrab = 0, nTrab = 0;
        double eSin = 0, eCos = 0, sSin = 0, sCos = 0;
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
            if (ASISTIO.equals(estado)) sinHorario++;
            if (f.get("entrada") != null) {
                marcados++;
                double a = angulo((String) f.get("entrada"));
                eSin += Math.sin(a);
                eCos += Math.cos(a);
                if (f.get("salida") != null && !Boolean.TRUE.equals(f.get("salidaAutomatica"))) {
                    nSalida++;
                    double b = angulo((String) f.get("salida"));
                    sSin += Math.sin(b);
                    sCos += Math.cos(b);
                }
            }
            if (f.get("minutosTrabajados") != null) { minTrab += ((Number) f.get("minutosTrabajados")).longValue(); nTrab++; }
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
        kpis.put("diasMarcados", marcados);
        kpis.put("diasSinHorario", sinHorario);
        kpis.put("entradaPromedio", marcados == 0 ? null : horaPromedio(eSin / marcados, eCos / marcados));
        kpis.put("salidaPromedio", nSalida == 0 ? null : horaPromedio(sSin / nSalida, sCos / nSalida));
        kpis.put("minutosPorDia", nTrab == 0 ? null : Math.round((double) minTrab / nTrab));
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

    /** "HH:mm" como ángulo del día (0 a 2π), para el promedio circular. */
    private static double angulo(String hhmm) {
        return LocalTime.parse(hhmm).toSecondOfDay() * Math.PI / 43200;
    }

    private static String hhmm(java.sql.Time t) {
        if (t == null) return null;
        LocalTime l = t.toLocalTime();
        return String.format("%02d:%02d", l.getHour(), l.getMinute());
    }
}
