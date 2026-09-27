package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "asistencia")
public class Asistencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;
    
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;
    
    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;
    
    @Column(name = "hora_salida")
    private LocalTime horaSalida;
    
    @Column(name = "estado", length = 20)
    private String estado = "ausente"; // presente, ausente, tardanza, permiso
    
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;
    
    @Column(name = "salida_automatica", nullable = false)
    private Boolean salidaAutomatica = false;

    @Column(name = "ip_entrada", length = 45)
    private String ipEntrada;

    @Column(name = "ip_salida", length = 45)
    private String ipSalida;

    /** Como se verifico cada marcacion: facial o manual. */
    @Column(name = "metodo_entrada", length = 20)
    private String metodoEntrada;

    @Column(name = "metodo_salida", length = 20)
    private String metodoSalida;

    /** Distancia facial obtenida al marcar (menor = mas parecido). */
    @Column(name = "distancia_entrada", precision = 6, scale = 4)
    private java.math.BigDecimal distanciaEntrada;

    @Column(name = "distancia_salida", precision = 6, scale = 4)
    private java.math.BigDecimal distanciaSalida;
    
    // Constructores
    public Asistencia() {}
    
    public Asistencia(Empleado empleado, LocalDate fecha) {
        this.empleado = empleado;
        this.fecha = fecha;
    }

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Empleado getEmpleado() {
		return empleado;
	}

	public void setEmpleado(Empleado empleado) {
		this.empleado = empleado;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public void setFecha(LocalDate fecha) {
		this.fecha = fecha;
	}

	public LocalTime getHoraEntrada() {
		return horaEntrada;
	}

	public void setHoraEntrada(LocalTime horaEntrada) {
		this.horaEntrada = horaEntrada;
	}

	public LocalTime getHoraSalida() {
		return horaSalida;
	}

	public void setHoraSalida(LocalTime horaSalida) {
		this.horaSalida = horaSalida;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getObservaciones() {
		return observaciones;
	}

	public void setObservaciones(String observaciones) {
		this.observaciones = observaciones;
	}

	public Boolean getSalidaAutomatica() {
		return salidaAutomatica;
	}

	public void setSalidaAutomatica(Boolean salidaAutomatica) {
		this.salidaAutomatica = salidaAutomatica;
	}    
    

	public String getIpEntrada() { return ipEntrada; }
	public void setIpEntrada(String ipEntrada) { this.ipEntrada = ipEntrada; }

	public String getIpSalida() { return ipSalida; }
	public void setIpSalida(String ipSalida) { this.ipSalida = ipSalida; }

	public String getMetodoEntrada() { return metodoEntrada; }
	public void setMetodoEntrada(String metodoEntrada) { this.metodoEntrada = metodoEntrada; }

	public String getMetodoSalida() { return metodoSalida; }
	public void setMetodoSalida(String metodoSalida) { this.metodoSalida = metodoSalida; }

	public java.math.BigDecimal getDistanciaEntrada() { return distanciaEntrada; }
	public void setDistanciaEntrada(java.math.BigDecimal distanciaEntrada) { this.distanciaEntrada = distanciaEntrada; }

	public java.math.BigDecimal getDistanciaSalida() { return distanciaSalida; }
	public void setDistanciaSalida(java.math.BigDecimal distanciaSalida) { this.distanciaSalida = distanciaSalida; }
}
