package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "respuestas_evento",
       uniqueConstraints = @UniqueConstraint(columnNames = {"evento_id", "empleado_id"}))
public class RespuestaEvento {

    public enum ConfirmacionAsistencia {
        CONFIRMADO, NO_ASISTIRE, PENDIENTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @ManyToOne
    @JoinColumn(name = "opcion_id")
    private OpcionEvento opcion;

    @Column(name = "respuesta_si_no")
    private Boolean respuestaSiNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmacion_asistencia", length = 20)
    private ConfirmacionAsistencia confirmacionAsistencia;

    @Column(name = "comentario", columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta = LocalDateTime.now();

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    public RespuestaEvento() {}

    @PreUpdate
    public void preUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public OpcionEvento getOpcion() { return opcion; }
    public void setOpcion(OpcionEvento opcion) { this.opcion = opcion; }

    public Boolean getRespuestaSiNo() { return respuestaSiNo; }
    public void setRespuestaSiNo(Boolean respuestaSiNo) { this.respuestaSiNo = respuestaSiNo; }

    public ConfirmacionAsistencia getConfirmacionAsistencia() { return confirmacionAsistencia; }
    public void setConfirmacionAsistencia(ConfirmacionAsistencia confirmacionAsistencia) {
        this.confirmacionAsistencia = confirmacionAsistencia;
    }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getFechaRespuesta() { return fechaRespuesta; }
    public void setFechaRespuesta(LocalDateTime fechaRespuesta) { this.fechaRespuesta = fechaRespuesta; }

    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }
}
