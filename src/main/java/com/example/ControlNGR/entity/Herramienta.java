package com.example.ControlNGR.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Acceso a una herramienta web del equipo (vista "+ Herramientas"). */
@Entity
@Table(name = "herramientas")
public class Herramienta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String titulo;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false, length = 500)
    private String url;

    /** PNG de hasta 128x128 en base64 (sin el prefijo data:). */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String logo;

    @Column(name = "todos_los_roles", nullable = false)
    private Boolean todosLosRoles = true;

    /** Roles que la ven cuando no es para todos. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "herramienta_roles", joinColumns = @JoinColumn(name = "herramienta_id"))
    @Column(name = "rol", length = 20)
    private Set<String> roles = new LinkedHashSet<>();

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public Boolean getTodosLosRoles() { return todosLosRoles; }
    public void setTodosLosRoles(Boolean todosLosRoles) { this.todosLosRoles = todosLosRoles; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
