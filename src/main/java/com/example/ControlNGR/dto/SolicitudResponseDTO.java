package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.Solicitud;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SolicitudResponseDTO {
    private Integer id;
    private Integer empleadoId;
    private String empleadoNombre;
    private String tipo;
    private LocalDateTime fechaSolicitud;
    private String fechaInicio;
    private String fechaFin;
    private String motivo;
    private String estado;
    private String aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private boolean tieneConflictos;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    public SolicitudResponseDTO() {}
    
    public SolicitudResponseDTO(Solicitud solicitud) {
        this.id = solicitud.getId();
        this.empleadoId = solicitud.getEmpleado().getId();
        this.empleadoNombre = solicitud.getEmpleado().getNombre();
        this.tipo = solicitud.getTipo();
        this.fechaSolicitud = solicitud.getFechaSolicitud();
        this.fechaInicio = solicitud.getFechaInicio().format(DATE_FORMATTER);
        this.fechaFin = solicitud.getFechaFin().format(DATE_FORMATTER);
        this.motivo = solicitud.getMotivo();
        this.estado = solicitud.getEstado();
        this.aprobadoPor = solicitud.getAprobadoPor() != null ? solicitud.getAprobadoPor().getNombre() : null;
        this.fechaAprobacion = solicitud.getFechaAprobacion();
        this.tieneConflictos = false;
    }
    
    // Getters y Setters (todos iguales)
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }
    
    public String getEmpleadoNombre() { return empleadoNombre; }
    public void setEmpleadoNombre(String empleadoNombre) { this.empleadoNombre = empleadoNombre; }
    
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    
    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }
    
    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }
    
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    
    public String getAprobadoPor() { return aprobadoPor; }
    public void setAprobadoPor(String aprobadoPor) { this.aprobadoPor = aprobadoPor; }
    
    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }
    
    public boolean isTieneConflictos() { return tieneConflictos; }
    public void setTieneConflictos(boolean tieneConflictos) { this.tieneConflictos = tieneConflictos; }
}