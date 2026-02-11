package com.example.ControlNGR.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import com.example.ControlNGR.entity.Asistencia;

public class AsistenciaResponseDTO {
    private Integer id;
    private Integer empleadoId;
    private String empleadoNombre;
    private LocalDate fecha;
    private LocalTime horaEntrada;
    private LocalTime horaSalida;
    private String estado;
    private String observaciones;
    private Boolean salidaAutomatica;
    
    public AsistenciaResponseDTO() {}
    
    public AsistenciaResponseDTO(Asistencia asistencia) {
        this.id = asistencia.getId();
        this.empleadoId = asistencia.getEmpleado().getId();
        this.empleadoNombre = asistencia.getEmpleado().getNombre();
        this.fecha = asistencia.getFecha();
        this.horaEntrada = asistencia.getHoraEntrada();
        this.horaSalida = asistencia.getHoraSalida();
        this.estado = asistencia.getEstado();
        this.observaciones = asistencia.getObservaciones();
        this.salidaAutomatica = asistencia.getSalidaAutomatica();
    }

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getEmpleadoId() {
		return empleadoId;
	}

	public void setEmpleadoId(Integer empleadoId) {
		this.empleadoId = empleadoId;
	}

	public String getEmpleadoNombre() {
		return empleadoNombre;
	}

	public void setEmpleadoNombre(String empleadoNombre) {
		this.empleadoNombre = empleadoNombre;
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
    
}