package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.Horario;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class HorarioResponseDTO {
    private Integer id;
    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoRol;
    private String diaSemana;
    private String horaEntrada;
    private String horaSalida;
    private String horaAlmuerzoInicio;
    private String horaAlmuerzoFin;
    private String tipoDia;
    private String turno;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public HorarioResponseDTO() {}

    public HorarioResponseDTO(Horario horario) {
        this.id = horario.getId();
        this.empleadoId = horario.getEmpleado().getId();
        this.empleadoNombre = horario.getEmpleado().getNombre();
        this.empleadoRol = horario.getEmpleado().getRol();
        this.diaSemana = horario.getDiaSemana();
        this.horaEntrada = formatTime(horario.getHoraEntrada());
        this.horaSalida = formatTime(horario.getHoraSalida());
        this.horaAlmuerzoInicio = formatTime(horario.getHoraAlmuerzoInicio());
        this.horaAlmuerzoFin = formatTime(horario.getHoraAlmuerzoFin());
        this.tipoDia = horario.getTipoDia() != null ? horario.getTipoDia() : "normal";
        this.turno = horario.getTurno() != null ? horario.getTurno() : "manana";
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : null;
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public String getEmpleadoNombre() { return empleadoNombre; }
    public void setEmpleadoNombre(String empleadoNombre) { this.empleadoNombre = empleadoNombre; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

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

    public String getEmpleadoRol() { return empleadoRol; }
    public void setEmpleadoRol(String empleadoRol) { this.empleadoRol = empleadoRol; }

    public String getTurno() { return turno; }
    public void setTurno(String turno) { this.turno = turno; }
}
