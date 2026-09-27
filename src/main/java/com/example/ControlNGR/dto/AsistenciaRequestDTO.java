package com.example.ControlNGR.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public class AsistenciaRequestDTO {
    private Integer empleadoId;
    private String tipo; // "entrada" o "salida"
    private LocalDate fecha;
    private LocalTime hora;
    private String observaciones;
    /** Descriptor facial (128 valores) capturado al marcar. El servidor lo compara con el registrado. */
    private double[] descriptor;
    
    // Getters y Setters
    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }
    
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public double[] getDescriptor() { return descriptor; }
    public void setDescriptor(double[] descriptor) { this.descriptor = descriptor; }
}
