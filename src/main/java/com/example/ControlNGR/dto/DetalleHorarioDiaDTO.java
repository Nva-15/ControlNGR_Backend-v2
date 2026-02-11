package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DetalleHorarioDiaDTO {

    private Integer id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fecha;
    private String diaSemana;
    private String horaEntrada;
    private String horaSalida;
    private String horaAlmuerzoInicio;
    private String horaAlmuerzoFin;
    private String tipoDia;
    private String turno;
    private String origenTipoDia;
    private Integer solicitudId;
    private boolean esHoy;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Constructores
    public DetalleHorarioDiaDTO() {}

    public DetalleHorarioDiaDTO(HorarioSemanalDetalle detalle) {
        this.id = detalle.getId();
        this.fecha = detalle.getFecha();
        this.diaSemana = detalle.getDiaSemana();
        this.horaEntrada = formatTime(detalle.getHoraEntrada());
        this.horaSalida = formatTime(detalle.getHoraSalida());
        this.horaAlmuerzoInicio = formatTime(detalle.getHoraAlmuerzoInicio());
        this.horaAlmuerzoFin = formatTime(detalle.getHoraAlmuerzoFin());
        this.tipoDia = detalle.getTipoDia();
        this.turno = detalle.getTurno();
        this.origenTipoDia = detalle.getOrigenTipoDia();
        this.solicitudId = detalle.getSolicitudRef() != null ? detalle.getSolicitudRef().getId() : null;
        this.esHoy = LocalDate.now().equals(detalle.getFecha());
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : null;
    }

    // Getters y Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

    public String getHoraEntrada() {
        return horaEntrada;
    }

    public void setHoraEntrada(String horaEntrada) {
        this.horaEntrada = horaEntrada;
    }

    public String getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(String horaSalida) {
        this.horaSalida = horaSalida;
    }

    public String getHoraAlmuerzoInicio() {
        return horaAlmuerzoInicio;
    }

    public void setHoraAlmuerzoInicio(String horaAlmuerzoInicio) {
        this.horaAlmuerzoInicio = horaAlmuerzoInicio;
    }

    public String getHoraAlmuerzoFin() {
        return horaAlmuerzoFin;
    }

    public void setHoraAlmuerzoFin(String horaAlmuerzoFin) {
        this.horaAlmuerzoFin = horaAlmuerzoFin;
    }

    public String getTipoDia() {
        return tipoDia;
    }

    public void setTipoDia(String tipoDia) {
        this.tipoDia = tipoDia;
    }

    public String getTurno() {
        return turno;
    }

    public void setTurno(String turno) {
        this.turno = turno;
    }

    public String getOrigenTipoDia() {
        return origenTipoDia;
    }

    public void setOrigenTipoDia(String origenTipoDia) {
        this.origenTipoDia = origenTipoDia;
    }

    public Integer getSolicitudId() {
        return solicitudId;
    }

    public void setSolicitudId(Integer solicitudId) {
        this.solicitudId = solicitudId;
    }

    public boolean isEsHoy() {
        return esHoy;
    }

    public void setEsHoy(boolean esHoy) {
        this.esHoy = esHoy;
    }
}
