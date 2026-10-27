package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Asistencia;
import com.example.ControlNGR.entity.Departamento;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.TipoUsuario;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.service.ResumenAsistenciaService.Dia;
import com.example.ControlNGR.service.ResumenAsistenciaService.Programado;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Clasificación de cada día del resumen y reglas de acceso del dashboard y de "Mi récord". */
class ResumenAsistenciaTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDateTime DESPUES = LocalDateTime.of(2026, 11, 2, 12, 0);

    private static Empleado empleado(String rol) {
        Empleado e = new Empleado();
        e.setId(10);
        e.setNombre("Jean Lopez");
        e.setActivo(true);
        Departamento d = new Departamento();
        d.setId(2);
        e.setDepartamento(d);
        TipoUsuario t = new TipoUsuario();
        t.setCodigo(rol);
        t.setMarcaAsistencia(true);
        t.setConHorario(true);
        Usuario u = new Usuario();
        u.setTipoUsuario(t);
        u.setEmpleado(e);
        e.setUsuario(u);
        return e;
    }

    private static Asistencia marco(LocalTime entrada, LocalTime salida, boolean auto) {
        Asistencia a = new Asistencia();
        a.setHoraEntrada(entrada);
        a.setHoraSalida(salida);
        a.setSalidaAutomatica(auto);
        a.setEstado("presente");
        return a;
    }

    private static final Programado MANANA = new Programado("normal", "manana", "SP", LocalTime.of(8, 30), LocalTime.of(17, 30));

    @Test
    void clasificaPuntualidadConTolerancia() {
        Empleado e = empleado("tecnico");
        Dia temprano = ResumenAsistenciaService.clasificar(e, LUNES, MANANA, marco(LocalTime.of(8, 20), LocalTime.of(17, 35), false), 10, DESPUES);
        assertEquals("a_tiempo", temprano.estado());
        assertEquals(-10, temprano.minutosDiferencia());
        assertEquals(555, temprano.minutosTrabajados());
        assertEquals(540, temprano.minutosProgramados());
        assertEquals("SP", temprano.funciones());

        Dia enTolerancia = ResumenAsistenciaService.clasificar(e, LUNES, MANANA, marco(LocalTime.of(8, 40), null, false), 10, DESPUES);
        assertEquals("a_tiempo", enTolerancia.estado());
        assertNull(enTolerancia.minutosTrabajados(), "sin salida no suma horas");

        Dia tarde = ResumenAsistenciaService.clasificar(e, LUNES, MANANA, marco(LocalTime.of(8, 52), LocalTime.of(17, 30), true), 10, DESPUES);
        assertEquals("tardanza", tarde.estado());
        assertEquals(22, tarde.minutosRetraso());
        assertTrue(tarde.salidaAutomatica());
        assertNull(tarde.minutosTrabajados(), "la salida automática no cuenta como horas trabajadas");
    }

    @Test
    void faltaPendienteYDiasNoLaborables() {
        Empleado e = empleado("tecnico");
        assertEquals("falta", ResumenAsistenciaService.clasificar(e, LUNES, MANANA, null, 10, DESPUES).estado());
        // Hoy, antes de que empiece su turno: aún pendiente
        LocalDateTime hoyTemprano = LUNES.atTime(8, 0);
        assertEquals("pendiente", ResumenAsistenciaService.clasificar(e, LUNES, MANANA, null, 10, hoyTemprano).estado());
        assertEquals("falta", ResumenAsistenciaService.clasificar(e, LUNES, MANANA, null, 10, LUNES.atTime(9, 0)).estado());

        Programado descanso = new Programado("descanso", null, null, null, null);
        assertEquals("descanso", ResumenAsistenciaService.clasificar(e, LUNES, descanso, null, 10, DESPUES).estado());
        Programado vacaciones = new Programado("vacaciones", null, null, null, null);
        Dia v = ResumenAsistenciaService.clasificar(e, LUNES, vacaciones, null, 10, DESPUES);
        assertEquals("vacaciones", v.estado());
        assertNull(v.minutosProgramados());

        // Sin horario: si marcó, "asistió"; si no, el día no cuenta
        assertEquals("asistio", ResumenAsistenciaService.clasificar(e, LUNES, null, marco(LocalTime.of(9, 0), null, false), 10, DESPUES).estado());
        assertNull(ResumenAsistenciaService.clasificar(e, LUNES, null, null, 10, DESPUES));
    }

    @Test
    void turnoDeNocheYDiferenciaCercaDeMedianoche() {
        Empleado e = empleado("noc");
        Programado noche = new Programado("normal", "tarde", "LT", LocalTime.of(23, 0), LocalTime.of(7, 0));
        Dia d = ResumenAsistenciaService.clasificar(e, LUNES, noche, marco(LocalTime.of(0, 15), LocalTime.of(7, 0), false), 10, DESPUES);
        assertEquals(75, d.minutosDiferencia(), "00:15 frente a 23:00 son 75 min tarde, no 22 h antes");
        assertEquals("tardanza", d.estado());
        assertEquals(405, d.minutosTrabajados());
        assertEquals(480, d.minutosProgramados());
        assertEquals(-20, ResumenAsistenciaService.diferenciaMinutos(LocalTime.of(0, 10), LocalTime.of(23, 50)));
    }

    @Test
    void rangosDeLlegada() {
        int[] limites = { -15, -5, 0, 10, 20, 30 };
        assertEquals(0, ReporteAsistenciaService.rango(-40, limites));
        assertEquals(0, ReporteAsistenciaService.rango(-15, limites));
        assertEquals(1, ReporteAsistenciaService.rango(-14, limites));
        assertEquals(3, ReporteAsistenciaService.rango(10, limites), "en el límite de la tolerancia aún es a tiempo");
        assertEquals(4, ReporteAsistenciaService.rango(11, limites));
        assertEquals(6, ReporteAsistenciaService.rango(31, limites));
    }

    @Test
    void alcancePorRolYRecordPropio() {
        ParametroService parametros = mock(ParametroService.class);
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate plano = mock(JdbcTemplate.class);
        when(jdbc.getJdbcTemplate()).thenReturn(plano);
        ReporteAsistenciaService servicio = new ReporteAsistenciaService(jdbc, null, null, null, parametros, null);

        assertNull(servicio.rolesVisibles(empleado("jefe").getUsuario()), "las jefaturas ven a todos");
        Set<String> supervisor = servicio.rolesVisibles(empleado("supervisor").getUsuario());
        assertEquals(Set.of("tecnico", "hd", "noc", "bo"), supervisor, "el supervisor, al personal operativo de todas las áreas");
        assertTrue(ReporteAsistenciaService.dentroDelAlcance(supervisor, "NOC"));
        assertFalse(ReporteAsistenciaService.dentroDelAlcance(supervisor, "supervisor"), "no ve a otros supervisores");
        assertFalse(ReporteAsistenciaService.dentroDelAlcance(supervisor, "jefe"));
        assertEquals(Set.of("asistente"), servicio.rolesVisibles(empleado("gestor").getUsuario()));
        assertThrows(AccesoDenegadoException.class, () -> servicio.rolesVisibles(empleado("tecnico").getUsuario()));

        Empleado e = empleado("tecnico");
        e.setIngreso(LocalDate.of(2023, 1, 2));
        when(parametros.texto(eq(ParametroService.MI_RECORD_DESDE), any())).thenReturn("INGRESO");
        assertEquals(LocalDate.of(2023, 1, 2), servicio.miRecordDesde(e));
        when(parametros.texto(eq(ParametroService.MI_RECORD_DESDE), any())).thenReturn("2026-01-01");
        assertEquals(LocalDate.of(2026, 1, 1), servicio.miRecordDesde(e));
        e.setIngreso(LocalDate.of(2026, 3, 15));
        assertEquals(LocalDate.of(2026, 3, 15), servicio.miRecordDesde(e), "nunca antes del inicio de su historial");

        // Marcó antes de su fecha de ingreso (mal registrada): el historial empieza en su primera marcación
        when(plano.queryForObject(anyString(), eq(LocalDate.class), any(Object[].class))).thenReturn(LocalDate.of(2025, 6, 2));
        when(parametros.texto(eq(ParametroService.MI_RECORD_DESDE), any())).thenReturn("INGRESO");
        assertEquals(LocalDate.of(2025, 6, 2), servicio.miRecordDesde(e));

        when(parametros.booleano(eq(ParametroService.MI_RECORD_HABILITADO), anyBoolean())).thenReturn(false);
        assertThrows(AccesoDenegadoException.class, servicio::miRecord, "apagado: nadie ve su récord");
    }

    @Test
    void hoyYGranularidad() {
        assertEquals("dia", ReporteAsistenciaService.granularidad(31));
        assertEquals("semana", ReporteAsistenciaService.granularidad(92));
        assertEquals("mes", ReporteAsistenciaService.granularidad(400));
        assertEquals("08:30", ReporteAsistenciaService.horaDeSegundos(8 * 3600 + 30 * 60 + 10));
        // Promedio circular: 23:50 y 00:10 promedian 00:00 (no 12:00)
        double a = (23 * 3600 + 50 * 60) * Math.PI / 43200, b = 10 * 60 * Math.PI / 43200;
        assertEquals("00:00", ReporteAsistenciaService.horaPromedio((Math.sin(a) + Math.sin(b)) / 2, (Math.cos(a) + Math.cos(b)) / 2));

        List<Map<String, Object>> hoy = List.of(
                Map.of("estado", "a_tiempo", "enTurno", true), Map.of("estado", "tardanza", "enTurno", true),
                Map.of("estado", "falta", "enTurno", false), Map.of("estado", "pendiente", "enTurno", false),
                Map.of("estado", "vacaciones", "enTurno", false), Map.of("estado", "descanso", "enTurno", false),
                Map.of("estado", "asistio", "enTurno", false));
        Map<String, Object> k = ReporteAsistenciaService.contarHoy(hoy);
        assertEquals(4L, k.get("programados"));
        assertEquals(1L, k.get("porLlegar"));
        assertEquals(2L, k.get("enTurno"));
        assertEquals(1L, k.get("ausencias"));
        assertEquals(1L, k.get("descansos"));
        assertEquals(1L, k.get("sinHorario"));
        assertTrue(ReporteAsistenciaService.urgencia("falta") < ReporteAsistenciaService.urgencia("tardanza"));
        assertTrue(ReporteAsistenciaService.urgencia("pendiente") < ReporteAsistenciaService.urgencia("a_tiempo"));
        assertTrue(ReporteAsistenciaService.urgencia("vacaciones") < ReporteAsistenciaService.urgencia("descanso"));
    }

    @Test
    void horarioBaseSoloDesdeSuVigencia() {
        com.example.ControlNGR.entity.Horario h = new com.example.ControlNGR.entity.Horario();
        h.setVigenteDesde(LUNES);
        assertFalse(h.vigenteEn(LUNES.minusDays(1)), "antes de su vigencia no programa (no genera faltas)");
        assertTrue(h.vigenteEn(LUNES));
        h.setVigenteDesde(null);
        assertTrue(h.vigenteEn(LUNES.minusYears(3)));
    }
}
