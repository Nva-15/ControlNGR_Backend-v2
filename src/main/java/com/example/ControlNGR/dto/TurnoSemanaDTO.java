package com.example.ControlNGR.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * Turnos de varios días de una persona, cada día con el suyo. En una semana de horarios {@code clave} es la
 * fecha (AAAA-MM-DD); en el horario base, el día de la semana (lunes ... domingo).
 *
 * @param guardarComoBase solo en una semana: guarda también estos turnos como horario base de la persona
 */
public record TurnoSemanaDTO(List<Dia> dias, Boolean guardarComoBase) {

    public static final String LABORAL = "normal";
    public static final String DESCANSO = "descanso";
    /** Días de descanso permitidos por semana. */
    public static final int MAX_DESCANSOS = 1;

    /** Un día: laboral (con turno, horas y funciones) o descanso. */
    public record Dia(String clave, String tipoDia, String turno, LocalTime horaEntrada, LocalTime horaSalida,
                      LocalTime horaAlmuerzoInicio, LocalTime horaAlmuerzoFin, List<String> funciones) {

        public boolean esLaboral() {
            return LABORAL.equals(tipoDia);
        }

        public boolean esTarde() {
            return "tarde".equalsIgnoreCase(turno);
        }

        public String turnoNormalizado() {
            return esTarde() ? "tarde" : "manana";
        }

        public LocalTime almuerzoInicio() {
            return esTarde() ? null : horaAlmuerzoInicio;
        }

        public LocalTime almuerzoFin() {
            return esTarde() ? null : horaAlmuerzoFin;
        }

        void validar() {
            if (clave == null || clave.isBlank()) {
                throw new IllegalArgumentException("Falta el día");
            }
            if (!LABORAL.equals(tipoDia) && !DESCANSO.equals(tipoDia)) {
                throw new IllegalArgumentException("Cada día debe ser laboral o descanso");
            }
            if (!esLaboral()) return;
            if (horaEntrada == null || horaSalida == null) {
                throw new IllegalArgumentException("Indique la hora de entrada y de salida (" + clave + ")");
            }
            if (horaEntrada.equals(horaSalida)) {
                throw new IllegalArgumentException("La hora de salida debe ser distinta a la de entrada (" + clave + ")");
            }
            if (!esTarde()) {
                if ((horaAlmuerzoInicio == null) != (horaAlmuerzoFin == null)) {
                    throw new IllegalArgumentException("Indique el inicio y el fin del refrigerio (" + clave + ")");
                }
                if (horaAlmuerzoInicio != null && !horaAlmuerzoInicio.isBefore(horaAlmuerzoFin)) {
                    throw new IllegalArgumentException("El fin del refrigerio debe ser posterior a su inicio (" + clave + ")");
                }
            }
        }
    }

    /** Valida cada día; los días no se repiten. */
    public void validar() {
        if (dias == null || dias.isEmpty()) {
            throw new IllegalArgumentException("Indique al menos un día");
        }
        if (dias.stream().map(Dia::clave).distinct().count() != dias.size()) {
            throw new IllegalArgumentException("Un día aparece más de una vez");
        }
        dias.forEach(Dia::validar);
    }

    /** Mensaje cuando una persona queda con más descansos de los permitidos. */
    public static String mensajeDescansos(String nombre, long descansos) {
        return "Solo se permite " + MAX_DESCANSOS + " día de descanso por semana: "
                + (nombre != null ? nombre + " quedaría con " : "quedarían ") + descansos + ".";
    }
}
