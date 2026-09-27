package com.example.ControlNGR.security;

import java.util.List;
import java.util.Set;

/**
 * Codigos de rol (tabla tipos_usuario) y agrupaciones usadas en permisos.
 * Quien aprueba a quien NO esta aqui: se configura en la tabla reglas_aprobacion.
 */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String DIRECTOR = "director";
    public static final String GERENTE = "gerente";
    public static final String JEFE = "jefe";
    public static final String SUPERVISOR = "supervisor";
    public static final String GESTOR = "gestor";
    public static final String TECNICO = "tecnico";
    public static final String HD = "hd";
    public static final String NOC = "noc";
    public static final String BO = "bo";
    public static final String ASISTENTE = "asistente";

    /** Alta direccion: reemplaza al antiguo rol "admin" en la gestion diaria. */
    public static final Set<String> GERENCIA = Set.of(DIRECTOR, GERENTE, JEFE);

    /** Personal operativo a cargo de los supervisores. */
    public static final Set<String> OPERATIVOS = Set.of(TECNICO, HD, NOC, BO);

    /** Roles con personal a cargo (gestionan horarios, eventos, empleados). */
    public static final Set<String> GESTION = Set.of(DIRECTOR, GERENTE, JEFE, SUPERVISOR, GESTOR);

    /** Todos los roles de personal (todos menos admin). */
    public static final List<String> PERSONAL = List.of(
            DIRECTOR, GERENTE, JEFE, SUPERVISOR, GESTOR, TECNICO, HD, NOC, BO, ASISTENTE);

    // Autoridades de Spring Security (ROLE_ + codigo en mayusculas)
    public static final String[] AUTH_PERSONAL = PERSONAL.stream().map(String::toUpperCase).toArray(String[]::new);
    public static final String[] AUTH_PERSONAL_Y_ADMIN = concat(AUTH_PERSONAL, "ADMIN");
    public static final String[] AUTH_GESTION = {"DIRECTOR", "GERENTE", "JEFE", "SUPERVISOR", "GESTOR"};
    public static final String[] AUTH_GESTION_Y_ADMIN = concat(AUTH_GESTION, "ADMIN");

    private Roles() {}

    public static boolean esAdmin(String rol) {
        return ADMIN.equalsIgnoreCase(rol);
    }

    public static boolean esGerencia(String rol) {
        return rol != null && GERENCIA.contains(rol.toLowerCase());
    }

    public static boolean esGestion(String rol) {
        return rol != null && GESTION.contains(rol.toLowerCase());
    }

    /** Rango jerarquico dentro de gerencia: director 3, gerente 2, jefe 1, el resto 0. */
    public static int rango(String rol) {
        if (rol == null) return 0;
        return switch (rol.toLowerCase()) {
            case DIRECTOR -> 3;
            case GERENTE -> 2;
            case JEFE -> 1;
            default -> 0;
        };
    }

    /**
     * Si el rol editor puede administrar (editar/activar/eliminar) a un empleado con rol objetivo.
     * La gerencia solo administra a rangos inferiores al suyo.
     */
    public static boolean puedeAdministrar(String rolEditor, String rolObjetivo) {
        if (rolEditor == null) return false;
        String editor = rolEditor.toLowerCase();
        String objetivo = rolObjetivo != null ? rolObjetivo.toLowerCase() : "";
        if (esAdmin(editor)) return true;
        if (esGerencia(editor)) return !esAdmin(objetivo) && rango(objetivo) < rango(editor);
        if (SUPERVISOR.equals(editor)) return OPERATIVOS.contains(objetivo);
        if (GESTOR.equals(editor)) return ASISTENTE.equals(objetivo);
        return false;
    }

    /** Si el rol editor puede asignar el rol indicado a un empleado (crear o cambiar rol). */
    public static boolean puedeAsignarRol(String rolEditor, String rolNuevo) {
        if (esAdmin(rolEditor)) return true;
        return esGerencia(rolEditor) && rango(rolNuevo) < rango(rolEditor);
    }

    private static String[] concat(String[] base, String extra) {
        String[] r = java.util.Arrays.copyOf(base, base.length + 1);
        r[base.length] = extra;
        return r;
    }
}
