package com.example.ControlNGR.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Período en curso de cada alerta: mes calendario o semana de lunes a domingo (semana ISO). */
class AlertaAsistenciaTest {

    @Test
    void periodoDelMesYDeLaSemana() {
        LocalDate martes = LocalDate.of(2026, 10, 27);
        var mes = AlertaAsistenciaService.periodo("MES", martes);
        assertEquals(LocalDate.of(2026, 10, 1), mes.desde());
        assertEquals(martes, mes.hasta());
        assertEquals("2026-10", mes.clave());

        var semana = AlertaAsistenciaService.periodo("SEMANA", martes);
        assertEquals(LocalDate.of(2026, 10, 26), semana.desde(), "empieza el lunes");
        assertEquals("2026-W44", semana.clave());

        // 1 de enero de 2027 (viernes) pertenece a la semana 53 de 2026
        assertEquals("2026-W53", AlertaAsistenciaService.periodo("SEMANA", LocalDate.of(2027, 1, 1)).clave());
    }
}
