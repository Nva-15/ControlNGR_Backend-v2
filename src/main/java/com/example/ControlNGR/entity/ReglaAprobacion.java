package com.example.ControlNGR.entity;

import jakarta.persistence.*;

/** Indica que un rol aprobador puede aprobar/rechazar las solicitudes de un rol solicitante. */
@Entity
@Table(name = "reglas_aprobacion")
public class ReglaAprobacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_usuario_solicitante_id", nullable = false)
    private TipoUsuario solicitante;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_usuario_aprobador_id", nullable = false)
    private TipoUsuario aprobador;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public TipoUsuario getSolicitante() { return solicitante; }
    public void setSolicitante(TipoUsuario solicitante) { this.solicitante = solicitante; }

    public TipoUsuario getAprobador() { return aprobador; }
    public void setAprobador(TipoUsuario aprobador) { this.aprobador = aprobador; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
