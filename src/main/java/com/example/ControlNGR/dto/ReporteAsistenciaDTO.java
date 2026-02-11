package com.example.ControlNGR.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ReporteAsistenciaDTO {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoRol;
    private String empleadoCargo;
    private LocalDate fecha;
    private String diaSemana;
    private String horarioEntrada;    // scheduled (formatted HH:mm)
    private String horaEntradaReal;   // actual (formatted HH:mm)
    private String horarioSalida;     // scheduled (formatted HH:mm)
    private String horaSalidaReal;    // actual (formatted HH:mm)
    private String estado;            // "A tiempo", "Tardanza", "Falta", "Descanso", "Vacaciones", "Compensado", "Permiso", "Sin horario", "Pendiente"
    private Long minutosRetraso;
    private String tipoDia;
    private String turno;
    private String observaciones;
    private Boolean salidaAutomatica;

    public ReporteAsistenciaDTO() {}

    // Getters y Setters
    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public String getEmpleadoNombre() { return empleadoNombre; }
    public void setEmpleadoNombre(String empleadoNombre) { this.empleadoNombre = empleadoNombre; }

    public String getEmpleadoRol() { return empleadoRol; }
    public void setEmpleadoRol(String empleadoRol) { this.empleadoRol = empleadoRol; }

    public String getEmpleadoCargo() { return empleadoCargo; }
    public void setEmpleadoCargo(String empleadoCargo) { this.empleadoCargo = empleadoCargo; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public String getHorarioEntrada() { return horarioEntrada; }
    public void setHorarioEntrada(String horarioEntrada) { this.horarioEntrada = horarioEntrada; }
    public void setHorarioEntradaFromTime(LocalTime time) {
        this.horarioEntrada = time != null ? time.format(TIME_FORMATTER) : null;
    }

    public String getHoraEntradaReal() { return horaEntradaReal; }
    public void setHoraEntradaReal(String horaEntradaReal) { this.horaEntradaReal = horaEntradaReal; }
    public void setHoraEntradaRealFromTime(LocalTime time) {
        this.horaEntradaReal = time != null ? time.format(TIME_FORMATTER) : null;
    }

    public String getHorarioSalida() { return horarioSalida; }
    public void setHorarioSalida(String horarioSalida) { this.horarioSalida = horarioSalida; }
    public void setHorarioSalidaFromTime(LocalTime time) {
        this.horarioSalida = time != null ? time.format(TIME_FORMATTER) : null;
    }

    public String getHoraSalidaReal() { return horaSalidaReal; }
    public void setHoraSalidaReal(String horaSalidaReal) { this.horaSalidaReal = horaSalidaReal; }
    public void setHoraSalidaRealFromTime(LocalTime time) {
        this.horaSalidaReal = time != null ? time.format(TIME_FORMATTER) : null;
    }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Long getMinutosRetraso() { return minutosRetraso; }
    public void setMinutosRetraso(Long minutosRetraso) { this.minutosRetraso = minutosRetraso; }

    public String getTipoDia() { return tipoDia; }
    public void setTipoDia(String tipoDia) { this.tipoDia = tipoDia; }

    public String getTurno() { return turno; }
    public void setTurno(String turno) { this.turno = turno; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public Boolean getSalidaAutomatica() { return salidaAutomatica; }
    public void setSalidaAutomatica(Boolean salidaAutomatica) { this.salidaAutomatica = salidaAutomatica; }
}
