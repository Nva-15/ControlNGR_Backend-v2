package com.example.ControlNGR.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Programación de los respaldos automáticos (una sola fila, id = 1). */
@Entity
@Table(name = "respaldo_programacion")
public class RespaldoProgramacion {
    public static final int ID = 1;

    @Id
    private Integer id = ID;

    /** NINGUNA, DIARIA, SEMANAL o MENSUAL. */
    @Column(nullable = false, length = 10)
    private String frecuencia = "NINGUNA";

    @Column(nullable = false)
    private LocalTime hora = LocalTime.of(2, 0);

    /** 1 = lunes ... 7 = domingo. */
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana = 1;

    @Column(name = "dia_mes", nullable = false)
    private Integer diaMes = 1;

    @Column(nullable = false)
    private Integer conservar = 10;

    @Column(name = "incluir_archivos", nullable = false)
    private Boolean incluirArchivos = true;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    public Integer getDiaSemana() { return diaSemana; }
    public void setDiaSemana(Integer diaSemana) { this.diaSemana = diaSemana; }
    public Integer getDiaMes() { return diaMes; }
    public void setDiaMes(Integer diaMes) { this.diaMes = diaMes; }
    public Integer getConservar() { return conservar; }
    public void setConservar(Integer conservar) { this.conservar = conservar; }
    public Boolean getIncluirArchivos() { return incluirArchivos; }
    public void setIncluirArchivos(Boolean incluirArchivos) { this.incluirArchivos = incluirArchivos; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
}
