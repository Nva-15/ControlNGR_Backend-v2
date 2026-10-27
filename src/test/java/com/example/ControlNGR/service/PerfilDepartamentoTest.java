package com.example.ControlNGR.service;

import com.example.ControlNGR.security.Roles;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Valores que se proponen al registrar a alguien en un departamento, y quién puede cambiar roles. */
class PerfilDepartamentoTest {

    @Test
    void proponeLoMasComunDelEquipo() {
        assertEquals("Operador NOC", EmpleadoService.masComun(List.of("Operador NOC", "Supervisor de NOC", "Operador NOC")));
        assertEquals("A", EmpleadoService.masComun(List.of("B", "A")), "a igual frecuencia, el primero alfabéticamente");
        assertEquals("noc", EmpleadoService.masComun(Arrays.asList(null, " ", "noc")), "ignora vacíos");
        assertNull(EmpleadoService.masComun(List.of()));
    }

    @Test
    void elSupervisorCambiaRolesSoloEntreElPersonalOperativo() {
        assertTrue(Roles.puedeAdministrar("supervisor", "hd"));
        assertTrue(Roles.puedeAsignarRol("supervisor", "noc"), "HD → NOC");
        assertFalse(Roles.puedeAsignarRol("supervisor", "supervisor"));
        assertFalse(Roles.puedeAsignarRol("supervisor", "jefe"));
        assertTrue(Roles.puedeAdministrar("jefe", "supervisor"), "el jefe edita a sus supervisores");
        assertTrue(Roles.puedeAsignarRol("admin", "director"), "el administrador, todo");
    }
}
