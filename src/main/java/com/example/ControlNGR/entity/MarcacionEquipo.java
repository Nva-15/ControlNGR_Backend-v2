package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Cómo marca asistencia un equipo (departamento): con reconocimiento facial o con el botón. */
@Entity
@Table(name = "marcacion_equipo")
public class MarcacionEquipo {

    public static final String FACIAL = "FACIAL";
    public static final String BOTON = "BOTON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(optional = false)
    @JoinColumn(name = "departamento_id", nullable = false, unique = true)
    private Departamento departamento;

    @Column(nullable = false, length = 10)
    private String modo = BOTON;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    public Integer getId() { return id; }
    public Departamento getDepartamento() { return departamento; }
    public void setDepartamento(Departamento departamento) { this.departamento = departamento; }
    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
}
