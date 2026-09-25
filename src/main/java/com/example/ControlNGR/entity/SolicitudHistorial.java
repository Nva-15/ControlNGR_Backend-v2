package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Auditoria de cada cambio de estado de una solicitud. */
@Entity
@Table(name = "solicitud_historial")
public class SolicitudHistorial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private String estadoNuevo;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "comentario", columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public SolicitudHistorial() {}

    public SolicitudHistorial(Solicitud solicitud, String estadoAnterior, String estadoNuevo,
                              Usuario usuario, String comentario) {
        this.solicitud = solicitud;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuario = usuario;
        this.comentario = comentario;
    }

    public Integer getId() { return id; }
    public Solicitud getSolicitud() { return solicitud; }
    public String getEstadoAnterior() { return estadoAnterior; }
    public String getEstadoNuevo() { return estadoNuevo; }
    public Usuario getUsuario() { return usuario; }
    public String getComentario() { return comentario; }
    public LocalDateTime getFecha() { return fecha; }
}
