package com.example.ControlNGR.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimiteIntentosLoginTest {

    @Test
    void bloqueaTrasCincoFallosYSeReiniciaConExito() {
        LimiteIntentosLogin limite = new LimiteIntentosLogin();
        for (int i = 0; i < 4; i++) limite.registrarFallo("70000001");
        assertEquals(0, limite.minutosBloqueado("70000001"), "4 fallos aún no bloquean");
        limite.registrarFallo(" 70000001 ");
        assertTrue(limite.minutosBloqueado("70000001") >= 14, "el 5.º fallo bloquea 15 minutos");
        assertEquals(0, limite.minutosBloqueado("otro"), "no afecta a otros usuarios");
        limite.registrarExito("70000001");
        assertEquals(0, limite.minutosBloqueado("70000001"));
    }

    @Test
    void usuarioSinDistinguirMayusculas() {
        LimiteIntentosLogin limite = new LimiteIntentosLogin();
        for (int i = 0; i < 5; i++) limite.registrarFallo(i % 2 == 0 ? "Admin" : "ADMIN");
        assertTrue(limite.minutosBloqueado("admin") > 0);
    }
}
