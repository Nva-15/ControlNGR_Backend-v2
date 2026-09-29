package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.RespaldoProgramacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class RespaldoProgramacionTest {

    private static RespaldoProgramacion prog(String frecuencia, String hora, int diaSemana, int diaMes) {
        RespaldoProgramacion p = new RespaldoProgramacion();
        p.setFrecuencia(frecuencia);
        p.setHora(LocalTime.parse(hora));
        p.setDiaSemana(diaSemana);
        p.setDiaMes(diaMes);
        return p;
    }

    private static LocalDateTime t(String s) { return LocalDateTime.parse(s); }

    @Test
    void sinProgramacion() {
        RespaldoProgramacion p = prog("NINGUNA", "02:00", 1, 1);
        assertNull(RespaldoService.ultimaEjecucion(p, t("2026-09-29T10:00")));
        assertNull(RespaldoService.proximaEjecucion(p, t("2026-09-29T10:00")));
    }

    @Test
    void diaria() {
        RespaldoProgramacion p = prog("DIARIA", "02:00", 1, 1);
        assertEquals(t("2026-09-29T02:00"), RespaldoService.ultimaEjecucion(p, t("2026-09-29T02:00")));
        assertEquals(t("2026-09-28T02:00"), RespaldoService.ultimaEjecucion(p, t("2026-09-29T01:59")));
        assertEquals(t("2026-09-30T02:00"), RespaldoService.proximaEjecucion(p, t("2026-09-29T02:00")));
        assertEquals(t("2026-09-29T02:00"), RespaldoService.proximaEjecucion(p, t("2026-09-29T01:59")));
    }

    @Test
    void semanal() {
        // 2026-09-29 es martes; programado los viernes (5) a las 18:30
        RespaldoProgramacion p = prog("SEMANAL", "18:30", 5, 1);
        assertEquals(t("2026-09-25T18:30"), RespaldoService.ultimaEjecucion(p, t("2026-09-29T10:00")));
        assertEquals(t("2026-10-02T18:30"), RespaldoService.proximaEjecucion(p, t("2026-09-29T10:00")));
        assertEquals(t("2026-09-25T18:30"), RespaldoService.ultimaEjecucion(p, t("2026-10-02T18:29")));
        assertEquals(t("2026-10-02T18:30"), RespaldoService.ultimaEjecucion(p, t("2026-10-02T18:30")));
        assertEquals(t("2026-10-09T18:30"), RespaldoService.proximaEjecucion(p, t("2026-10-02T18:30")));
    }

    @Test
    void mensualConMesCorto() {
        RespaldoProgramacion p = prog("MENSUAL", "23:00", 1, 31);
        // Septiembre tiene 30 días: se usa el 30
        assertEquals(t("2026-09-30T23:00"), RespaldoService.proximaEjecucion(p, t("2026-09-29T10:00")));
        assertEquals(t("2026-08-31T23:00"), RespaldoService.ultimaEjecucion(p, t("2026-09-29T10:00")));
        // Febrero 2027: 28 días
        assertEquals(t("2027-02-28T23:00"), RespaldoService.proximaEjecucion(p, t("2027-02-01T00:00")));
        assertEquals(t("2027-01-31T23:00"), RespaldoService.ultimaEjecucion(p, t("2027-02-28T22:59")));
    }

    @Test
    void mensualDiaUno() {
        RespaldoProgramacion p = prog("MENSUAL", "00:00", 1, 1);
        assertEquals(t("2026-10-01T00:00"), RespaldoService.proximaEjecucion(p, t("2026-09-29T10:00")));
        assertEquals(t("2026-09-01T00:00"), RespaldoService.ultimaEjecucion(p, t("2026-09-29T10:00")));
        assertEquals(t("2027-01-01T00:00"), RespaldoService.proximaEjecucion(p, t("2026-12-15T10:00")));
    }
}
