package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "eventos")
public class Evento {

    public enum TipoEvento {
        ENCUESTA, SI_NO, ASISTENCIA, INFORMATIVO
    }

    public enum EstadoEvento {
        BORRADOR, ACTIVO, FINALIZADO, CANCELADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 20)
    private TipoEvento tipoEvento;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20)
    private EstadoEvento estado = EstadoEvento.BORRADOR;

    @Column(name = "roles_visibles", length = 255)
    private String rolesVisibles = "admin,supervisor,tecnico,hd,noc";

    @Column(name = "permite_comentarios")
    private Boolean permiteComentarios = true;

    @Column(name = "requiere_respuesta")
    private Boolean requiereRespuesta = false;

    @ManyToOne
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Empleado creadoPor;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OpcionEvento> opciones = new ArrayList<>();

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespuestaEvento> respuestas = new ArrayList<>();

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComentarioEvento> comentarios = new ArrayList<>();

    public Evento() {}

    // Helper methods para opciones
    public void addOpcion(OpcionEvento opcion) {
        opciones.add(opcion);
        opcion.setEvento(this);
    }

    public void removeOpcion(OpcionEvento opcion) {
        opciones.remove(opcion);
        opcion.setEvento(null);
    }

    // Helper para roles visibles
    public List<String> getRolesVisiblesList() {
        if (rolesVisibles == null || rolesVisibles.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(rolesVisibles.split(","));
    }

    public void setRolesVisiblesList(List<String> roles) {
        this.rolesVisibles = String.join(",", roles);
    }

    public boolean esVisibleParaRol(String rol) {
        if (rolesVisibles == null || rolesVisibles.isEmpty()) {
            return true;
        }
        return getRolesVisiblesList().contains(rol.toLowerCase());
    }

    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public TipoEvento getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(TipoEvento tipoEvento) { this.tipoEvento = tipoEvento; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public EstadoEvento getEstado() { return estado; }
    public void setEstado(EstadoEvento estado) { this.estado = estado; }

    public String getRolesVisibles() { return rolesVisibles; }
    public void setRolesVisibles(String rolesVisibles) { this.rolesVisibles = rolesVisibles; }

    public Boolean getPermiteComentarios() { return permiteComentarios; }
    public void setPermiteComentarios(Boolean permiteComentarios) { this.permiteComentarios = permiteComentarios; }

    public Boolean getRequiereRespuesta() { return requiereRespuesta; }
    public void setRequiereRespuesta(Boolean requiereRespuesta) { this.requiereRespuesta = requiereRespuesta; }

    public Empleado getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Empleado creadoPor) { this.creadoPor = creadoPor; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public List<OpcionEvento> getOpciones() { return opciones; }
    public void setOpciones(List<OpcionEvento> opciones) { this.opciones = opciones; }

    public List<RespuestaEvento> getRespuestas() { return respuestas; }
    public void setRespuestas(List<RespuestaEvento> respuestas) { this.respuestas = respuestas; }

    public List<ComentarioEvento> getComentarios() { return comentarios; }
    public void setComentarios(List<ComentarioEvento> comentarios) { this.comentarios = comentarios; }
}
