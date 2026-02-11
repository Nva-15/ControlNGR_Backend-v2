package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes")
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado; // El que solicita

    @Column(name = "tipo", length = 20)
    private String tipo;

    @Column(name = "fecha_solicitud")
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "estado", length = 20)
    private String estado = "pendiente";

    @ManyToOne
    @JoinColumn(name = "aprobado_por")
    private Empleado aprobadoPor; // CORREGIDO: Ahora es un Empleado (Jefe/Supervisor)

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    public Solicitud() {}

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Empleado getAprobadoPor() { return aprobadoPor; } // Getter actualizado
    public void setAprobadoPor(Empleado aprobadoPor) { this.aprobadoPor = aprobadoPor; } // Setter actualizado

    public LocalDateTime getFechaAprobacion() { return fechaAprobacion; }
    public void setFechaAprobacion(LocalDateTime fechaAprobacion) { this.fechaAprobacion = fechaAprobacion; }
}