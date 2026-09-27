package com.example.ControlNGR.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventoEnlaceTest {

    @Test
    void enlaceVacioEsSinEnlace() {
        assertNull(EventoService.normalizarEnlace(null));
        assertNull(EventoService.normalizarEnlace("   "));
    }

    @Test
    void aceptaDireccionesWeb() {
        assertEquals("https://teams.microsoft.com/l/meetup-join/abc?x=1",
                EventoService.normalizarEnlace("  https://teams.microsoft.com/l/meetup-join/abc?x=1 "));
        assertEquals("http://10.92.104.20/formulario", EventoService.normalizarEnlace("http://10.92.104.20/formulario"));
    }

    @Test
    void rechazaEsquemasPeligrososYTextoSuelto() {
        assertThrows(RuntimeException.class, () -> EventoService.normalizarEnlace("javascript:alert(1)"));
        assertThrows(RuntimeException.class, () -> EventoService.normalizarEnlace("teams.microsoft.com/reunion"));
        assertThrows(RuntimeException.class, () -> EventoService.normalizarEnlace("https://ejemplo.com/a b"));
        assertThrows(RuntimeException.class, () -> EventoService.normalizarEnlace("https://\"><script>"));
        assertThrows(RuntimeException.class, () -> EventoService.normalizarEnlace("ftp://servidor/archivo"));
    }
}
