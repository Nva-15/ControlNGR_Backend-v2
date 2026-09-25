package com.example.ControlNGR.entity;

import jakarta.persistence.*;

/** Rol del sistema (admin, director, gerente, jefe, supervisor, gestor, tecnico, hd, noc, bo, asistente). */
@Entity
@Table(name = "tipos_usuario")
public class TipoUsuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo", nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "nivel_jerarquia", nullable = false)
    private Integer nivelJerarquia = 0;

    @Column(name = "puede_solicitar", nullable = false)
    private Boolean puedeSolicitar = true;

    @Column(name = "marca_asistencia", nullable = false)
    private Boolean marcaAsistencia = true;

    @Column(name = "es_sistema", nullable = false)
    private Boolean esSistema = false;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getNivelJerarquia() { return nivelJerarquia; }
    public void setNivelJerarquia(Integer nivelJerarquia) { this.nivelJerarquia = nivelJerarquia; }

    public Boolean getPuedeSolicitar() { return puedeSolicitar; }
    public void setPuedeSolicitar(Boolean puedeSolicitar) { this.puedeSolicitar = puedeSolicitar; }

    public Boolean getMarcaAsistencia() { return marcaAsistencia; }
    public void setMarcaAsistencia(Boolean marcaAsistencia) { this.marcaAsistencia = marcaAsistencia; }

    public Boolean getEsSistema() { return esSistema; }
    public void setEsSistema(Boolean esSistema) { this.esSistema = esSistema; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
