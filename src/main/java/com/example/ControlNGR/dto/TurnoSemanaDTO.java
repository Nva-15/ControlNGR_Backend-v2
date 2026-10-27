package com.example.ControlNGR.dto;

import java.time.LocalTime;
import java.util.Map;

/**
 * Un mismo turno para varios días de una persona: los días "normal" lo reciben y los "descanso" quedan libres.
 * En una semana de horarios las claves de {@code dias} son fechas (AAAA-MM-DD); en el horario base, días
 * de la semana (lunes ... domingo).
 *
 * @param guardarComoBase solo en una semana: guarda también este turno como horario base de la persona
 */
public record TurnoSemanaDTO(
        String turno,
        LocalTime horaEntrada,
        LocalTime horaSalida,
        LocalTime horaAlmuerzoInicio,
        LocalTime horaAlmuerzoFin,
        Map<String, String> dias,
        Boolean guardarComoBase) {

    public static final String LABORAL = "normal";
    public static final String DESCANSO = "descanso";

    public boolean esTarde() {
        return "tarde".equalsIgnoreCase(turno);
    }

    public String turnoNormalizado() {
        return esTarde() ? "tarde" : "manana";
    }

    /** Valida que los días sean laboral/descanso y que el turno tenga horas coherentes. */
    public void validar() {
        if (dias == null || dias.isEmpty()) {
            throw new IllegalArgumentException("Indique los días que trabaja y los de descanso");
        }
        boolean hayLaboral = false;
        for (String tipo : dias.values()) {
            if (!LABORAL.equals(tipo) && !DESCANSO.equals(tipo)) {
                throw new IllegalArgumentException("Cada día debe ser laboral o descanso");
            }
            hayLaboral |= LABORAL.equals(tipo);
        }
        if (!hayLaboral) {
            return;
        }
        if (horaEntrada == null || horaSalida == null) {
            throw new IllegalArgumentException("Indique la hora de entrada y de salida");
        }
        if (horaEntrada.equals(horaSalida)) {
            throw new IllegalArgumentException("La hora de salida debe ser distinta a la de entrada");
        }
        if (!esTarde()) {
            if ((horaAlmuerzoInicio == null) != (horaAlmuerzoFin == null)) {
                throw new IllegalArgumentException("Indique el inicio y el fin del refrigerio");
            }
            if (horaAlmuerzoInicio != null && !horaAlmuerzoInicio.isBefore(horaAlmuerzoFin)) {
                throw new IllegalArgumentException("El fin del refrigerio debe ser posterior a su inicio");
            }
        }
    }

    public LocalTime almuerzoInicio() {
        return esTarde() ? null : horaAlmuerzoInicio;
    }

    public LocalTime almuerzoFin() {
        return esTarde() ? null : horaAlmuerzoFin;
    }
}
