package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solicitudes")
public class Solicitud {

    public static final String PENDIENTE = "pendiente";
    public static final String APROBADO = "aprobado";
    public static final String RECHAZADO = "rechazado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado; // El que solicita

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_solicitud_id", nullable = false)
    private TipoSolicitud tipoSolicitud;

    @ManyToOne
    @JoinColumn(name = "motivo_licencia_id")
    private MotivoLicencia motivoLicencia;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "dias_solicitados", nullable = false, precision = 5, scale = 1)
    private BigDecimal diasSolicitados = BigDecimal.ZERO;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PENDIENTE;

    @ManyToOne
    @JoinColumn(name = "aprobado_por")
    private Empleado aprobadoPor; // Jefe/Supervisor que gestiono

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "comentario_gestion", columnDefinition = "TEXT")
    private String comentarioGestion;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SolicitudEvidencia> evidencias = new ArrayList<>();

    public Solicitud() {}

    /** Codigo del tipo (vacaciones, compensacion, descanso_medico, licencia). */
    public String getTipo() {
        return tipoSolicitud != null ? tipoSolicitud.getCodigo() : null;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public TipoSolicitud getTipoSolicitud() { return tipoSolicitud; }
    public void setTipoSolicitud(TipoSolicitud tipoSolicitud) { this.tipoSolicitud = tipoSolicitud; }

    public MotivoLicencia getMotivoLicencia() { return motivoLicencia; }
    public void setMotivoLicencia(MotivoLicencia motivoLicencia) { this.motivoLicencia = motivoLicencia; }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public BigDecimal getDiasSolicitados() { return diasSolicitados; }
    public void setDiasSolicitados(BigDecimal diasSolicitados) { this.diasSolicitados = diasSolicitados; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Empleado getAprobadoPor() { return aprobadoPor; }
    public void setAprobadoPor(Empleado aprobadoPor) { this.aprobadoPor = aprobadoPor; }

    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }

    public String getComentarioGestion() { return comentarioGestion; }
    public void setComentarioGestion(String comentarioGestion) { this.comentarioGestion = comentarioGestion; }

    public List<SolicitudEvidencia> getEvidencias() { return evidencias; }
    public void setEvidencias(List<SolicitudEvidencia> evidencias) { this.evidencias = evidencias; }
}
