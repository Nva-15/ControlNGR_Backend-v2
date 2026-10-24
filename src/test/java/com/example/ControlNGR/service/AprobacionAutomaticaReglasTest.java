package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.AprobacionAutomatica;
import com.example.ControlNGR.entity.Solicitud;
import com.example.ControlNGR.service.AprobacionAutomaticaService.Accion;
import com.example.ControlNGR.service.AprobacionAutomaticaService.Dia;
import com.example.ControlNGR.service.AprobacionAutomaticaService.Resultado;
import com.example.ControlNGR.service.AprobacionAutomaticaService.Semana;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AprobacionAutomaticaReglasTest {

    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 28);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDateTime PEDIDA = LocalDateTime.of(2026, 10, 23, 10, 0);   // 5 días antes

    private static AprobacionAutomatica cfg(int cupo, int porEmpleado, int minimo, int horas, String alLlenarse) {
        AprobacionAutomatica c = new AprobacionAutomatica();
        c.setCupoSemanal(cupo);
        c.setMaximoPorEmpleado(porEmpleado);
        c.setMinimoEnTurno(minimo);
        c.setAntelacionHoras(horas);
        c.setAlLlenarse(alLlenarse);
        return c;
    }

    private static Resultado evaluar(AprobacionAutomatica c, boolean urgente, LocalDateTime pedida,
                                     int diasEmpleado, int diasEquipo, Dia dia) {
        return AprobacionAutomaticaService.evaluar(c, urgente, pedida, MIERCOLES,
                List.of(new Semana(LUNES, 1, diasEmpleado, diasEquipo)), List.of(dia));
    }

    private static Dia diaCon(int enTurno) { return new Dia(MIERCOLES, true, true, enTurno); }

    @Test
    void apruebaSiCumpleTodo() {
        Resultado r = evaluar(cfg(4, 1, 4, 48, "ESPERA"), false, PEDIDA, 0, 2, diaCon(7));
        assertEquals(Accion.APROBAR, r.accion());
        assertTrue(r.motivo().contains("3 de 4"), r.motivo());
    }

    @Test
    void urgenciaSiempreVaAlSupervisor() {
        Resultado r = evaluar(cfg(4, 1, 1, 0, "RECHAZO"), true, PEDIDA, 0, 4, diaCon(7));
        assertEquals(Accion.ESPERA, r.accion());
        assertTrue(r.motivo().startsWith("Urgencia"));
    }

    @Test
    void menosAntelacionVaAEspera() {
        Resultado r = evaluar(cfg(4, 1, 1, 48, "ESPERA"), false, LocalDateTime.of(2026, 10, 27, 9, 0), 0, 0, diaCon(7));
        assertEquals(Accion.ESPERA, r.accion());
        assertTrue(r.motivo().contains("48 h"));
    }

    @Test
    void superaMaximoPorEmpleado() {
        Resultado r = evaluar(cfg(4, 1, 1, 48, "RECHAZO"), false, PEDIDA, 1, 1, diaCon(7));
        assertEquals(Accion.ESPERA, r.accion(), "el límite por persona nunca rechaza");
        assertTrue(r.motivo().contains("máximo de 1"));
    }

    @Test
    void solicitudDeDosDiasSuperaElMaximo() {
        Resultado r = AprobacionAutomaticaService.evaluar(cfg(4, 1, 1, 48, "ESPERA"), false, PEDIDA, MIERCOLES,
                List.of(new Semana(LUNES, 2, 0, 0)), List.of(diaCon(7), new Dia(MIERCOLES.plusDays(1), true, true, 7)));
        assertEquals(Accion.ESPERA, r.accion());
    }

    @Test
    void cupoCompletoEsperaORechazoSegunConfiguracion() {
        assertEquals(Accion.ESPERA, evaluar(cfg(4, 1, 1, 48, "ESPERA"), false, PEDIDA, 0, 4, diaCon(7)).accion());
        Resultado r = evaluar(cfg(4, 1, 1, 48, "RECHAZO"), false, PEDIDA, 0, 4, diaCon(7));
        assertEquals(Accion.RECHAZAR, r.accion());
        assertTrue(r.motivo().contains("4 de 4"));
    }

    @Test
    void horarioNoPublicadoOSinTurno() {
        assertTrue(evaluar(cfg(4, 1, 1, 48, "ESPERA"), false, PEDIDA, 0, 0, new Dia(MIERCOLES, false, false, 0))
                .motivo().contains("no está publicado"));
        assertTrue(evaluar(cfg(4, 1, 1, 48, "ESPERA"), false, PEDIDA, 0, 0, new Dia(MIERCOLES, true, false, 5))
                .motivo().contains("No tiene turno"));
    }

    @Test
    void coberturaMinimaNocConTresPersonas() {
        AprobacionAutomatica noc = cfg(1, 1, 2, 72, "ESPERA");
        // 3 en turno: al compensar uno quedan 2 = mínimo -> se aprueba
        assertEquals(Accion.APROBAR, evaluar(noc, false, PEDIDA, 0, 0, diaCon(3)).accion());
        // 2 en turno (otro de descanso o vacaciones): quedaría 1 -> espera
        Resultado r = evaluar(noc, false, PEDIDA, 0, 0, diaCon(2));
        assertEquals(Accion.ESPERA, r.accion());
        assertTrue(r.motivo().contains("quedarían 1"));
        // el cupo semanal de 1 ya usado -> espera
        assertEquals(Accion.ESPERA, evaluar(noc, false, PEDIDA, 0, 1, diaCon(3)).accion());
    }

    @Test
    void diasEnRangoYLunes() {
        Solicitud s = new Solicitud();
        s.setFechaInicio(LocalDate.of(2026, 10, 31));
        s.setFechaFin(LocalDate.of(2026, 11, 3));
        assertEquals(2, AprobacionAutomaticaService.diasEnRango(s, LUNES, LUNES.plusDays(6)));
        assertEquals(2, AprobacionAutomaticaService.diasEnRango(s, LUNES.plusWeeks(1), LUNES.plusWeeks(1).plusDays(6)));
        assertEquals(LUNES, AprobacionAutomaticaService.lunes(LocalDate.of(2026, 11, 1)));
        assertEquals(LUNES, AprobacionAutomaticaService.lunes(LUNES));
    }
}
