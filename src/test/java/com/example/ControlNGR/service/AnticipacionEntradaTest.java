package com.example.ControlNGR.service;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** No se puede marcar entrada antes de N minutos del inicio del turno. */
class AnticipacionEntradaTest {

    private static final LocalTime TURNO = LocalTime.of(8, 0);

    @Test
    void permiteDesdeLosMinutosConfigurados() {
        AsistenciaService.validarAnticipacion(LocalTime.of(7, 55), TURNO, 5);
        AsistenciaService.validarAnticipacion(LocalTime.of(7, 58, 30), TURNO, 5);
        AsistenciaService.validarAnticipacion(LocalTime.of(8, 0), TURNO, 0);
    }

    @Test
    void rechazaAntesDeTiempoConElMensaje() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AsistenciaService.validarAnticipacion(LocalTime.of(7, 54, 59), TURNO, 5));
        assertTrue(e.getMessage().contains("08:00"), e.getMessage());
        assertTrue(e.getMessage().contains("07:55"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> AsistenciaService.validarAnticipacion(LocalTime.of(7, 59), TURNO, 0));
        assertThrows(IllegalStateException.class, () -> AsistenciaService.validarAnticipacion(LocalTime.of(6, 0), TURNO, 60));
    }

    @Test
    void tardanzaYRolesSinHorarioNoSeLimitan() {
        AsistenciaService.validarAnticipacion(LocalTime.of(9, 30), TURNO, 5);
        AsistenciaService.validarAnticipacion(LocalTime.of(23, 50), LocalTime.of(0, 0), 5);
        AsistenciaService.validarAnticipacion(LocalTime.of(3, 0), null, 5);
    }
}
