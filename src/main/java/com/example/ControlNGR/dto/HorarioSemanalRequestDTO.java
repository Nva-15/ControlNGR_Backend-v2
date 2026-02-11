package com.example.ControlNGR.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

public class HorarioSemanalRequestDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaInicio;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaFin;

    private Integer copiarDeId; // ID de semana anterior para copiar (opcional)
    private Integer creadoPorId;

    // Constructores
    public HorarioSemanalRequestDTO() {}

    public HorarioSemanalRequestDTO(LocalDate fechaInicio, LocalDate fechaFin, Integer creadoPorId) {
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.creadoPorId = creadoPorId;
    }

    // Getters y Setters
    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public Integer getCopiarDeId() {
        return copiarDeId;
    }

    public void setCopiarDeId(Integer copiarDeId) {
        this.copiarDeId = copiarDeId;
    }

    public Integer getCreadoPorId() {
        return creadoPorId;
    }

    public void setCreadoPorId(Integer creadoPorId) {
        this.creadoPorId = creadoPorId;
    }
}
