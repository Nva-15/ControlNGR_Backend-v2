package com.example.ControlNGR.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesTest {

    @Test
    void gerenciaSoloAdministraRangosInferiores() {
        assertTrue(Roles.puedeAdministrar("director", "gerente"));
        assertTrue(Roles.puedeAdministrar("gerente", "jefe"));
        assertTrue(Roles.puedeAdministrar("jefe", "supervisor"));
        assertTrue(Roles.puedeAdministrar("jefe", "tecnico"));
        assertFalse(Roles.puedeAdministrar("jefe", "gerente"));
        assertFalse(Roles.puedeAdministrar("jefe", "director"));
        assertFalse(Roles.puedeAdministrar("gerente", "director"));
        assertFalse(Roles.puedeAdministrar("jefe", "jefe"));
        assertFalse(Roles.puedeAdministrar("director", "admin"));
    }

    @Test
    void supervisorYGestorSoloSuPersonal() {
        assertTrue(Roles.puedeAdministrar("supervisor", "noc"));
        assertFalse(Roles.puedeAdministrar("supervisor", "supervisor"));
        assertFalse(Roles.puedeAdministrar("supervisor", "asistente"));
        assertTrue(Roles.puedeAdministrar("gestor", "asistente"));
        assertFalse(Roles.puedeAdministrar("gestor", "tecnico"));
        assertFalse(Roles.puedeAdministrar("tecnico", "tecnico"));
    }

    @Test
    void asignacionDeRoles() {
        assertTrue(Roles.puedeAsignarRol("admin", "director"));
        assertTrue(Roles.puedeAsignarRol("gerente", "jefe"));
        assertTrue(Roles.puedeAsignarRol("jefe", "supervisor"));
        assertFalse(Roles.puedeAsignarRol("jefe", "jefe"));
        assertFalse(Roles.puedeAsignarRol("jefe", "director"));
        assertFalse(Roles.puedeAsignarRol("supervisor", "tecnico"));
    }
}
