package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.Solicitud;
import com.example.ControlNGR.entity.SolicitudEvidencia;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SolicitudResponseDTO {
    private Integer id;
    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoRol;
    private String tipo;
    private String tipoNombre;
    private String descuentaDe;
    private BigDecimal diasSolicitados;
    private Integer motivoLicenciaId;
    private String motivoLicencia;
    private LocalDateTime fechaSolicitud;
    private String fechaInicio;
    private String fechaFin;
    private String motivo;
    private String estado;
    private String aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private String comentarioGestion;
    private boolean tieneConflictos;
    private List<EvidenciaDTO> evidencias;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public record EvidenciaDTO(Integer id, String nombre, String contentType, Long tamanoBytes, LocalDateTime fechaSubida) {
        static EvidenciaDTO de(SolicitudEvidencia e) {
            return new EvidenciaDTO(e.getId(), e.getNombreOriginal(), e.getContentType(), e.getTamanoBytes(), e.getFechaSubida());
        }
    }

    public SolicitudResponseDTO() {}

    public SolicitudResponseDTO(Solicitud solicitud) {
        this.id = solicitud.getId();
        this.empleadoId = solicitud.getEmpleado().getId();
        this.empleadoNombre = solicitud.getEmpleado().getNombre();
        this.empleadoRol = solicitud.getEmpleado().getRol();
        this.tipo = solicitud.getTipo();
        this.tipoNombre = solicitud.getTipoSolicitud().getNombre();
        this.descuentaDe = solicitud.getTipoSolicitud().getDescuentaDe();
        this.diasSolicitados = solicitud.getDiasSolicitados();
        if (solicitud.getMotivoLicencia() != null) {
            this.motivoLicenciaId = solicitud.getMotivoLicencia().getId();
            this.motivoLicencia = solicitud.getMotivoLicencia().getNombre();
        }
        this.fechaSolicitud = solicitud.getFechaSolicitud();
        this.fechaInicio = solicitud.getFechaInicio().format(DATE_FORMATTER);
        this.fechaFin = solicitud.getFechaFin().format(DATE_FORMATTER);
        this.motivo = solicitud.getMotivo();
        this.estado = solicitud.getEstado();
        this.aprobadoPor = solicitud.getAprobadoPor() != null ? solicitud.getAprobadoPor().getNombre() : null;
        this.fechaAprobacion = solicitud.getFechaAprobacion();
        this.comentarioGestion = solicitud.getComentarioGestion();
        this.evidencias = solicitud.getEvidencias().stream().map(EvidenciaDTO::de).toList();
        this.tieneConflictos = false;
    }

    public Integer getId() { return id; }
    public Integer getEmpleadoId() { return empleadoId; }
    public String getEmpleadoNombre() { return empleadoNombre; }
    public String getEmpleadoRol() { return empleadoRol; }
    public String getTipo() { return tipo; }
    public String getTipoNombre() { return tipoNombre; }
    public String getDescuentaDe() { return descuentaDe; }
    public BigDecimal getDiasSolicitados() { return diasSolicitados; }
    public Integer getMotivoLicenciaId() { return motivoLicenciaId; }
    public String getMotivoLicencia() { return motivoLicencia; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public String getFechaInicio() { return fechaInicio; }
    public String getFechaFin() { return fechaFin; }
    public String getMotivo() { return motivo; }
    public String getEstado() { return estado; }
    public String getAprobadoPor() { return aprobadoPor; }
    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public String getComentarioGestion() { return comentarioGestion; }
    public List<EvidenciaDTO> getEvidencias() { return evidencias; }

    public boolean isTieneConflictos() { return tieneConflictos; }
    public void setTieneConflictos(boolean tieneConflictos) { this.tieneConflictos = tieneConflictos; }
}
