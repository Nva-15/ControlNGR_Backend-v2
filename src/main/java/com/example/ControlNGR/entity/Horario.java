
package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "horarios")
public class Horario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;
    
    @Column(name = "dia_semana", length = 20)
    private String diaSemana; // lunes, martes, etc.
    
    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;
    
    @Column(name = "hora_salida")
    private LocalTime horaSalida;
    
    @Column(name = "hora_almuerzo_inicio")
    private LocalTime horaAlmuerzoInicio;
    
    @Column(name = "hora_almuerzo_fin")
    private LocalTime horaAlmuerzoFin;

    @Column(name = "tipo_dia", length = 20)
    private String tipoDia; // normal, descanso, compensado, vacaciones

    @Column(name = "turno", length = 20)
    private String turno; // manana, tarde

    // Constructores
    public Horario() {}

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

	public String getDiaSemana() {
		return diaSemana;
	}

	public void setDiaSemana(String diaSemana) {
		this.diaSemana = diaSemana;
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

	public LocalTime getHoraAlmuerzoInicio() {
		return horaAlmuerzoInicio;
	}

	public void setHoraAlmuerzoInicio(LocalTime horaAlmuerzoInicio) {
		this.horaAlmuerzoInicio = horaAlmuerzoInicio;
	}

	public LocalTime getHoraAlmuerzoFin() {
		return horaAlmuerzoFin;
	}

	public void setHoraAlmuerzoFin(LocalTime horaAlmuerzoFin) {
		this.horaAlmuerzoFin = horaAlmuerzoFin;
	}

	public String getTipoDia() {
		return tipoDia;
	}

	public void setTipoDia(String tipoDia) {
		this.tipoDia = tipoDia;
	}

	public String getTurno() {
		return turno;
	}

	public void setTurno(String turno) {
		this.turno = turno;
	}
}