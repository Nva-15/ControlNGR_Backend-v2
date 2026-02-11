package com.example.ControlNGR.dto;

import java.util.Map;
import java.util.HashMap;

public class HorarioSemanalDTO {
    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoRol;
    private String empleadoCargo;
    private Map<String, HorarioDiaDTO> horariosSemana;

    public HorarioSemanalDTO() {
        this.horariosSemana = new HashMap<>();
    }

    public HorarioSemanalDTO(Integer empleadoId, String empleadoNombre, String empleadoRol, String empleadoCargo) {
        this.empleadoId = empleadoId;
        this.empleadoNombre = empleadoNombre;
        this.empleadoRol = empleadoRol;
        this.empleadoCargo = empleadoCargo;
        this.horariosSemana = new HashMap<>();
    }

    // Getters y Setters
    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public String getEmpleadoNombre() { return empleadoNombre; }
    public void setEmpleadoNombre(String empleadoNombre) { this.empleadoNombre = empleadoNombre; }

    public String getEmpleadoRol() { return empleadoRol; }
    public void setEmpleadoRol(String empleadoRol) { this.empleadoRol = empleadoRol; }

    public String getEmpleadoCargo() { return empleadoCargo; }
    public void setEmpleadoCargo(String empleadoCargo) { this.empleadoCargo = empleadoCargo; }

    public Map<String, HorarioDiaDTO> getHorariosSemana() { return horariosSemana; }
    public void setHorariosSemana(Map<String, HorarioDiaDTO> horariosSemana) { this.horariosSemana = horariosSemana; }

    // Clase interna para el horario de cada dia
    public static class HorarioDiaDTO {
        private Integer id;
        private String horaEntrada;
        private String horaSalida;
        private String horaAlmuerzoInicio;
        private String horaAlmuerzoFin;
        private String tipoDia; // normal, descanso, compensado, vacaciones
        private String turno; // manana, tarde

        public HorarioDiaDTO() {}

        public HorarioDiaDTO(Integer id, String horaEntrada, String horaSalida,
                            String horaAlmuerzoInicio, String horaAlmuerzoFin,
                            String tipoDia, String turno) {
            this.id = id;
            this.horaEntrada = horaEntrada;
            this.horaSalida = horaSalida;
            this.horaAlmuerzoInicio = horaAlmuerzoInicio;
            this.horaAlmuerzoFin = horaAlmuerzoFin;
            this.tipoDia = tipoDia != null ? tipoDia : "normal";
            this.turno = turno != null ? turno : "manana";
        }

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getHoraEntrada() { return horaEntrada; }
        public void setHoraEntrada(String horaEntrada) { this.horaEntrada = horaEntrada; }

        public String getHoraSalida() { return horaSalida; }
        public void setHoraSalida(String horaSalida) { this.horaSalida = horaSalida; }

        public String getHoraAlmuerzoInicio() { return horaAlmuerzoInicio; }
        public void setHoraAlmuerzoInicio(String horaAlmuerzoInicio) { this.horaAlmuerzoInicio = horaAlmuerzoInicio; }

        public String getHoraAlmuerzoFin() { return horaAlmuerzoFin; }
        public void setHoraAlmuerzoFin(String horaAlmuerzoFin) { this.horaAlmuerzoFin = horaAlmuerzoFin; }

        public String getTipoDia() { return tipoDia; }
        public void setTipoDia(String tipoDia) { this.tipoDia = tipoDia; }

        public String getTurno() { return turno; }
        public void setTurno(String turno) { this.turno = turno; }
    }
}
