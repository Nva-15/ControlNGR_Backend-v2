package com.example.ControlNGR.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class FeriadoFechasTest {

    @Test
    void pascuaConocida() {
        assertEquals(LocalDate.of(2026, 4, 5), FeriadoFechas.domingoDePascua(2026));
        assertEquals(LocalDate.of(2027, 3, 28), FeriadoFechas.domingoDePascua(2027));
        assertEquals(LocalDate.of(2028, 4, 16), FeriadoFechas.domingoDePascua(2028));
        assertEquals(LocalDate.of(2029, 4, 1), FeriadoFechas.domingoDePascua(2029));
        assertEquals(LocalDate.of(2030, 4, 21), FeriadoFechas.domingoDePascua(2030));
    }

    @Test
    void semanaSantaSeRecalcula() {
        // Coincide con los feriados cargados para 2026 y 2027
        assertEquals(LocalDate.of(2027, 3, 25), FeriadoFechas.enAnio(LocalDate.of(2026, 4, 2), "Jueves Santo", 2027));
        assertEquals(LocalDate.of(2027, 3, 26), FeriadoFechas.enAnio(LocalDate.of(2026, 4, 3), "Viernes Santo", 2027));
        assertEquals(LocalDate.of(2028, 4, 13), FeriadoFechas.enAnio(LocalDate.of(2027, 3, 25), "JUEVES SANTO", 2028));
    }

    @Test
    void fijosMismoDiaYMes() {
        assertEquals(LocalDate.of(2028, 7, 28), FeriadoFechas.enAnio(LocalDate.of(2027, 7, 28), "Fiestas Patrias", 2028));
        assertEquals(LocalDate.of(2028, 8, 30), FeriadoFechas.enAnio(LocalDate.of(2027, 8, 30), "Santa Rosa de Lima", 2028));
        assertNull(FeriadoFechas.enAnio(LocalDate.of(2028, 2, 29), "Feriado de prueba", 2029));
        assertNull(FeriadoFechas.desfaseSemanaSanta("Día de Todos los Santos"));
    }
}
