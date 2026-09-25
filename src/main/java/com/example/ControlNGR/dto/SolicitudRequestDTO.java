package com.example.ControlNGR.dto;

import java.time.LocalDate;

public class SolicitudRequestDTO {
    /** Opcional: se toma del usuario autenticado. Si viene, debe coincidir. */
    private Integer empleadoId;
    /** Codigo del tipo: vacaciones, compensacion, descanso_medico, licencia. */
    private String tipo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String motivo;
    /** Requerido cuando el tipo es licencia. */
    private Integer motivoLicenciaId;

    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public Integer getMotivoLicenciaId() { return motivoLicenciaId; }
    public void setMotivoLicenciaId(Integer motivoLicenciaId) { this.motivoLicenciaId = motivoLicenciaId; }
}
