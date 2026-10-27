package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * Alertas de asistencia para jefaturas: faltas, faltas seguidas, tardanzas, tiempo de tardanza y salidas
 * automáticas por encima de un umbral configurable en el período (mes o semana en curso). Se calculan sobre
 * el resumen diario cada 15 minutos. Son indicadores: no generan sanciones ni avisan al colaborador.
 */
@Service
public class AlertaAsistenciaService {

    private static final Logger logger = LoggerFactory.getLogger(AlertaAsistenciaService.class);

    public static final String FALTAS = "FALTAS";
    public static final String FALTAS_SEGUIDAS = "FALTAS_SEGUIDAS";
    public static final String TARDANZAS = "TARDANZAS";
    public static final String MINUTOS_TARDE = "MINUTOS_TARDE";
    public static final String SALIDAS_AUTOMATICAS = "SALIDAS_AUTOMATICAS";
    public static final List<String> TIPOS = List.of(FALTAS, FALTAS_SEGUIDAS, TARDANZAS, MINUTOS_TARDE, SALIDAS_AUTOMATICAS);

    public record Config(String tipo, boolean activa, int umbral, String periodo) {}

    /** Período en curso de una alerta: rango y clave (2026-10 o 2026-W44). */
    record Periodo(LocalDate desde, LocalDate hasta, String clave) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final UsuarioActual usuarioActual;
    private final ReporteAsistenciaService reporteService;

    public AlertaAsistenciaService(NamedParameterJdbcTemplate jdbc, UsuarioActual usuarioActual,
                                   ReporteAsistenciaService reporteService) {
        this.jdbc = jdbc;
        this.usuarioActual = usuarioActual;
        this.reporteService = reporteService;
    }

    // ==================== CONFIGURACIÓN (panel admin) ====================

    public List<Config> configuracion() {
        Map<String, Config> porTipo = new HashMap<>();
        jdbc.getJdbcTemplate().query("SELECT tipo, activa, umbral, periodo FROM alerta_asistencia_config", rs -> {
            porTipo.put(rs.getString("tipo"), new Config(rs.getString("tipo"), rs.getBoolean("activa"), rs.getInt("umbral"), rs.getString("periodo")));
        });
        return TIPOS.stream().filter(porTipo::containsKey).map(porTipo::get).toList();
    }

    @Transactional
    public List<Config> guardar(List<Config> cambios) {
        for (Config c : cambios) {
            if (!TIPOS.contains(c.tipo())) throw new IllegalArgumentException("Alerta desconocida: " + c.tipo());
            int max = MINUTOS_TARDE.equals(c.tipo()) ? 10000 : 100;
            if (c.umbral() < 1 || c.umbral() > max) {
                throw new IllegalArgumentException("El umbral debe estar entre 1 y " + max);
            }
            String periodo = FALTAS_SEGUIDAS.equals(c.tipo()) ? "MES" : c.periodo();
            if (!"MES".equals(periodo) && !"SEMANA".equals(periodo)) {
                throw new IllegalArgumentException("El período debe ser MES o SEMANA");
            }
            jdbc.update("UPDATE alerta_asistencia_config SET activa = :a, umbral = :u, periodo = :p, actualizado_en = NOW() WHERE tipo = :t",
                    new MapSqlParameterSource().addValue("a", c.activa()).addValue("u", c.umbral()).addValue("p", periodo).addValue("t", c.tipo()));
        }
        detectar(LocalDate.now(), LocalDateTime.now());
        return configuracion();
    }

    static Periodo periodo(String tipo, LocalDate hoy) {
        if ("SEMANA".equals(tipo)) {
            LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
            return new Periodo(lunes, hoy, String.format("%d-W%02d", hoy.get(IsoFields.WEEK_BASED_YEAR), hoy.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        }
        return new Periodo(hoy.withDayOfMonth(1), hoy, String.format("%d-%02d", hoy.getYear(), hoy.getMonthValue()));
    }

    // ==================== DETECCIÓN ====================

    /** Cada 15 minutos, poco después de actualizar el resumen. */
    @Scheduled(cron = "0 5/15 * * * *", zone = "America/Lima")
    public void programado() {
        try {
            detectar(LocalDate.now(), LocalDateTime.now());
        } catch (Exception e) {
            logger.error("Error al calcular las alertas de asistencia: {}", e.getMessage(), e);
        }
    }

    /** Calcula las alertas del período en curso: crea las nuevas, actualiza su valor y quita las que ya no aplican. */
    @Transactional
    public synchronized int detectar(LocalDate hoy, LocalDateTime ahora) {
        int total = 0;
        for (Config c : configuracion()) {
            Periodo p = periodo(c.periodo(), hoy);
            Map<Integer, Object[]> encontradas = c.activa() ? calcular(c, p) : Map.of();
            Timestamp ts = Timestamp.valueOf(ahora);
            for (Map.Entry<Integer, Object[]> e : encontradas.entrySet()) {
                Object[] v = e.getValue(); // valor, detalle, departamento
                jdbc.update("INSERT INTO alerta_asistencia (tipo, empleado_id, departamento_id, periodo_clave, periodo_desde, periodo_hasta, "
                        + "valor, umbral, detalle, creado_en, actualizado_en) VALUES (:t, :e, :d, :k, :desde, :hasta, :v, :u, :det, :ts, :ts) "
                        + "ON DUPLICATE KEY UPDATE valor = VALUES(valor), umbral = VALUES(umbral), detalle = VALUES(detalle), "
                        + "periodo_hasta = VALUES(periodo_hasta), departamento_id = VALUES(departamento_id), actualizado_en = VALUES(actualizado_en)",
                        new MapSqlParameterSource().addValue("t", c.tipo()).addValue("e", e.getKey()).addValue("d", v[2])
                                .addValue("k", p.clave()).addValue("desde", Date.valueOf(p.desde())).addValue("hasta", Date.valueOf(p.hasta()))
                                .addValue("v", v[0]).addValue("u", c.umbral()).addValue("det", v[1]).addValue("ts", ts));
            }
            // Las del período en curso que dejaron de cumplirse (corrección de marcaciones, umbral mayor, alerta apagada)
            MapSqlParameterSource borrar = new MapSqlParameterSource().addValue("t", c.tipo()).addValue("k", p.clave());
            String sql = "DELETE FROM alerta_asistencia WHERE tipo = :t AND periodo_clave = :k";
            if (!encontradas.isEmpty()) {
                sql += " AND empleado_id NOT IN (:ids)";
                borrar.addValue("ids", encontradas.keySet());
            }
            jdbc.update(sql, borrar);
            total += encontradas.size();
        }
        return total;
    }

    /** empleadoId → [valor, detalle, departamento] de quienes alcanzan el umbral (solo personal activo). */
    private Map<Integer, Object[]> calcular(Config c, Periodo p) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("desde", Date.valueOf(p.desde())).addValue("hasta", Date.valueOf(p.hasta())).addValue("umbral", c.umbral());
        String base = " FROM asistencia_resumen_diario r JOIN empleados e ON e.id = r.empleado_id"
                + " WHERE e.activo = 1 AND r.fecha BETWEEN :desde AND :hasta";
        Map<Integer, Object[]> r = new LinkedHashMap<>();
        switch (c.tipo()) {
            case FALTAS -> jdbc.query("SELECT r.empleado_id id, MAX(r.departamento_id) d, SUM(r.estado = 'falta') n" + base
                    + " GROUP BY r.empleado_id HAVING n >= :umbral", params, rs -> {
                r.put(rs.getInt("id"), new Object[] { rs.getInt("n"), null, rs.getObject("d") });
            });
            case TARDANZAS -> jdbc.query("SELECT r.empleado_id id, MAX(r.departamento_id) d, SUM(r.estado = 'tardanza') n, "
                    + "ROUND(AVG(CASE WHEN r.estado = 'tardanza' THEN r.minutos_retraso END)) prom" + base
                    + " GROUP BY r.empleado_id HAVING n >= :umbral", params, rs -> {
                r.put(rs.getInt("id"), new Object[] { rs.getInt("n"), "prom. " + rs.getInt("prom") + " min", rs.getObject("d") });
            });
            case MINUTOS_TARDE -> jdbc.query("SELECT r.empleado_id id, MAX(r.departamento_id) d, COALESCE(SUM(r.minutos_retraso), 0) n, "
                    + "SUM(r.estado = 'tardanza') t" + base + " GROUP BY r.empleado_id HAVING n >= :umbral", params, rs -> {
                r.put(rs.getInt("id"), new Object[] { rs.getInt("n"), rs.getInt("t") + " tardanza(s)", rs.getObject("d") });
            });
            case SALIDAS_AUTOMATICAS -> jdbc.query("SELECT r.empleado_id id, MAX(r.departamento_id) d, SUM(r.salida_automatica) n" + base
                    + " GROUP BY r.empleado_id HAVING n >= :umbral", params, rs -> {
                r.put(rs.getInt("id"), new Object[] { rs.getInt("n"), null, rs.getObject("d") });
            });
            case FALTAS_SEGUIDAS -> {
                // Días laborales en orden: los descansos y vacaciones no cortan la racha; un día con entrada sí
                Map<Integer, int[]> racha = new HashMap<>(); // actual, máxima
                Map<Integer, Object> depto = new HashMap<>();
                Map<Integer, String> ultima = new HashMap<>();
                jdbc.query("SELECT r.empleado_id id, r.departamento_id d, r.fecha, r.estado" + base
                        + " AND r.estado IN ('a_tiempo', 'tardanza', 'falta') ORDER BY r.empleado_id, r.fecha", params, rs -> {
                    int id = rs.getInt("id");
                    int[] x = racha.computeIfAbsent(id, k -> new int[2]);
                    depto.put(id, rs.getObject("d"));
                    if ("falta".equals(rs.getString("estado"))) {
                        x[0]++;
                        if (x[0] >= x[1]) { x[1] = x[0]; ultima.put(id, rs.getDate("fecha").toLocalDate().toString()); }
                    } else {
                        x[0] = 0;
                    }
                });
                racha.forEach((id, x) -> {
                    if (x[1] >= c.umbral()) r.put(id, new Object[] { x[1], "hasta el " + ultima.get(id), depto.get(id) });
                });
            }
            default -> { }
        }
        return r;
    }

    // ==================== CONSULTA (jefaturas) ====================

    /** Alertas del período en curso dentro del alcance de quien consulta, con las nuevas marcadas. */
    public Map<String, Object> listar(Integer departamentoId) {
        Usuario u = usuarioActual.requerido();
        Set<String> alcance = reporteService.rolesVisibles(u);
        Integer depto = departamentoId;
        LocalDateTime visto = vistoEn(u.getId());
        LocalDate hoy = LocalDate.now();
        List<Map<String, Object>> alertas = new ArrayList<>();
        for (Config c : configuracion()) {
            if (!c.activa()) continue;
            Periodo p = periodo(c.periodo(), hoy);
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", c.tipo()).addValue("k", p.clave());
            String sql = "SELECT a.empleado_id, e.nombre, d.nombre depto, a.valor, a.umbral, a.detalle, a.periodo_desde, a.periodo_hasta, a.creado_en "
                    + "FROM alerta_asistencia a JOIN empleados e ON e.id = a.empleado_id LEFT JOIN departamentos d ON d.id = a.departamento_id "
                    + "WHERE a.tipo = :t AND a.periodo_clave = :k AND e.activo = 1";
            sql += ReporteAsistenciaService.condicionAlcance(alcance, params);
            if (depto != null) { sql += " AND a.departamento_id = :d"; params.addValue("d", depto); }
            jdbc.query(sql, params, rs -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tipo", c.tipo());
                m.put("periodo", c.periodo());
                m.put("empleadoId", rs.getInt("empleado_id"));
                m.put("nombre", rs.getString("nombre"));
                m.put("departamento", rs.getString("depto"));
                m.put("valor", rs.getInt("valor"));
                m.put("umbral", rs.getInt("umbral"));
                m.put("detalle", rs.getString("detalle"));
                m.put("desde", rs.getDate("periodo_desde").toLocalDate().toString());
                m.put("hasta", rs.getDate("periodo_hasta").toLocalDate().toString());
                LocalDateTime creada = rs.getTimestamp("creado_en").toLocalDateTime();
                m.put("nueva", visto == null || creada.isAfter(visto));
                alertas.add(m);
            });
        }
        // Las más graves primero: faltas seguidas, faltas, luego el resto; dentro de cada tipo, mayor valor
        alertas.sort(Comparator.comparingInt((Map<String, Object> m) -> TIPOS_GRAVEDAD.indexOf(m.get("tipo")))
                .thenComparing(m -> -(Integer) m.get("valor")));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("alertas", alertas);
        r.put("nuevas", alertas.stream().filter(m -> Boolean.TRUE.equals(m.get("nueva"))).count());
        return r;
    }

    private static final List<String> TIPOS_GRAVEDAD = List.of(FALTAS_SEGUIDAS, FALTAS, TARDANZAS, MINUTOS_TARDE, SALIDAS_AUTOMATICAS);

    /** Alertas nuevas (aún no vistas) para el contador del menú; 0 para quien no es jefatura. */
    public long contarNuevas(Usuario u) {
        if (u == null || !Roles.esGestion(u.getRol())) return 0;
        try {
            Set<String> alcance = reporteService.rolesVisibles(u);
            LocalDateTime visto = vistoEn(u.getId());
            LocalDate hoy = LocalDate.now();
            long n = 0;
            for (Config c : configuracion()) {
                if (!c.activa()) continue;
                MapSqlParameterSource params = new MapSqlParameterSource().addValue("t", c.tipo())
                        .addValue("k", periodo(c.periodo(), hoy).clave());
                String sql = "SELECT COUNT(*) FROM alerta_asistencia a JOIN empleados e ON e.id = a.empleado_id "
                        + "WHERE a.tipo = :t AND a.periodo_clave = :k AND e.activo = 1";
                sql += ReporteAsistenciaService.condicionAlcance(alcance, params);
                if (visto != null) { sql += " AND a.creado_en > :v"; params.addValue("v", Timestamp.valueOf(visto)); }
                Long x = jdbc.queryForObject(sql, params, Long.class);
                n += x == null ? 0 : x;
            }
            return n;
        } catch (RuntimeException e) {
            return 0; // rol sin alcance: sin alertas
        }
    }

    /** La jefatura vio sus alertas: las actuales dejan de contar como nuevas. */
    @Transactional
    public void marcarVistas() {
        Usuario u = usuarioActual.requerido();
        jdbc.update("INSERT INTO alerta_asistencia_lectura (usuario_id, visto_en) VALUES (:u, NOW()) "
                + "ON DUPLICATE KEY UPDATE visto_en = NOW()", new MapSqlParameterSource("u", u.getId()));
    }

    private LocalDateTime vistoEn(Integer usuarioId) {
        List<Timestamp> t = jdbc.query("SELECT visto_en FROM alerta_asistencia_lectura WHERE usuario_id = :u",
                new MapSqlParameterSource("u", usuarioId), (rs, i) -> rs.getTimestamp(1));
        return t.isEmpty() ? null : t.get(0).toLocalDateTime();
    }
}
