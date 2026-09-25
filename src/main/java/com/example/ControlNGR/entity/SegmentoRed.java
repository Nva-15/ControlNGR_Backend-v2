package com.example.ControlNGR.entity;

import jakarta.persistence.*;

/** Segmento de red desde el cual se permite marcar asistencia. */
@Entity
@Table(name = "segmentos_red")
public class SegmentoRed {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "patron", nullable = false, length = 50, unique = true)
    private String patron;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getPatron() { return patron; }
    public void setPatron(String patron) { this.patron = patron; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
