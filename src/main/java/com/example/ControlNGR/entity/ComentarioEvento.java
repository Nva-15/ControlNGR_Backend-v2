package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comentarios_evento")
public class ComentarioEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "comentario", nullable = false, columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "fecha_comentario")
    private LocalDateTime fechaComentario = LocalDateTime.now();

    public ComentarioEvento() {}

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getFechaComentario() { return fechaComentario; }
    public void setFechaComentario(LocalDateTime fechaComentario) { this.fechaComentario = fechaComentario; }
}
