package com.example.ControlNGR.dto;

import java.time.LocalTime;
import java.util.List;

public class HorarioRequestDTO {
    private Integer empleadoId;
    private String diaSemana;
    private LocalTime horaEntrada;
    private LocalTime horaSalida;
    private LocalTime horaAlmuerzoInicio;
    private LocalTime horaAlmuerzoFin;
    private String tipoDia;
    private String turno; // manana, tarde
    private List<String> dias; // Para aplicar a multiples dias

    // Getters y Setters
    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public LocalTime getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(LocalTime horaEntrada) { this.horaEntrada = horaEntrada; }

    public LocalTime getHoraSalida() { return horaSalida; }
    public void setHoraSalida(LocalTime horaSalida) { this.horaSalida = horaSalida; }

    public LocalTime getHoraAlmuerzoInicio() { return horaAlmuerzoInicio; }
    public void setHoraAlmuerzoInicio(LocalTime horaAlmuerzoInicio) { this.horaAlmuerzoInicio = horaAlmuerzoInicio; }

    public LocalTime getHoraAlmuerzoFin() { return horaAlmuerzoFin; }
    public void setHoraAlmuerzoFin(LocalTime horaAlmuerzoFin) { this.horaAlmuerzoFin = horaAlmuerzoFin; }

    public String getTipoDia() { return tipoDia; }
    public void setTipoDia(String tipoDia) { this.tipoDia = tipoDia; }

    public String getTurno() { return turno; }
    public void setTurno(String turno) { this.turno = turno; }

    public List<String> getDias() { return dias; }
    public void setDias(List<String> dias) { this.dias = dias; }
}
