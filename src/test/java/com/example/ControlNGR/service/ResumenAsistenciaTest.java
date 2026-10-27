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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
    void alcanceYRecordPropio() {
        ParametroService parametros = mock(ParametroService.class);
        ReporteAsistenciaService servicio = new ReporteAsistenciaService(null, null, null, null, parametros);

        assertNull(servicio.departamentoForzado(empleado("jefe").getUsuario()), "las jefaturas ven todos los equipos");
        assertEquals(2, servicio.departamentoForzado(empleado("supervisor").getUsuario()), "el supervisor, solo el suyo");
        assertThrows(AccesoDenegadoException.class, () -> servicio.departamentoForzado(empleado("tecnico").getUsuario()));

        Empleado e = empleado("tecnico");
        e.setIngreso(LocalDate.of(2023, 1, 2));
        when(parametros.texto(eq(ParametroService.MI_RECORD_DESDE), any())).thenReturn("INGRESO");
        assertEquals(LocalDate.of(2023, 1, 2), servicio.miRecordDesde(e));
        when(parametros.texto(eq(ParametroService.MI_RECORD_DESDE), any())).thenReturn("2026-01-01");
        assertEquals(LocalDate.of(2026, 1, 1), servicio.miRecordDesde(e));
        e.setIngreso(LocalDate.of(2026, 3, 15));
        assertEquals(LocalDate.of(2026, 3, 15), servicio.miRecordDesde(e), "nunca antes de su ingreso");

        when(parametros.booleano(eq(ParametroService.MI_RECORD_HABILITADO), anyBoolean())).thenReturn(false);
        assertThrows(AccesoDenegadoException.class, servicio::miRecord, "apagado: nadie ve su récord");
    }
}
