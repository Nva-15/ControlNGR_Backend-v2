package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Reglas de aprobación automática de compensaciones de un equipo (departamento). */
@Entity
@Table(name = "aprobacion_automatica")
public class AprobacionAutomatica {

    public static final String ESPERA = "ESPERA";
    public static final String RECHAZO = "RECHAZO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(optional = false)
    @JoinColumn(name = "departamento_id", nullable = false, unique = true)
    private Departamento departamento;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "cupo_semanal", nullable = false)
    private Integer cupoSemanal = 4;

    @Column(name = "maximo_por_empleado", nullable = false)
    private Integer maximoPorEmpleado = 1;

    @Column(name = "minimo_en_turno", nullable = false)
    private Integer minimoEnTurno = 1;

    @Column(name = "antelacion_horas", nullable = false)
    private Integer antelacionHoras = 48;

    @Column(name = "al_llenarse", nullable = false, length = 10)
    private String alLlenarse = ESPERA;

    @Column(name = "promover_espera", nullable = false)
    private Boolean promoverEspera = true;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;

    public Integer getId() { return id; }
    public Departamento getDepartamento() { return departamento; }
    public void setDepartamento(Departamento departamento) { this.departamento = departamento; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Integer getCupoSemanal() { return cupoSemanal; }
    public void setCupoSemanal(Integer cupoSemanal) { this.cupoSemanal = cupoSemanal; }
    public Integer getMaximoPorEmpleado() { return maximoPorEmpleado; }
    public void setMaximoPorEmpleado(Integer maximoPorEmpleado) { this.maximoPorEmpleado = maximoPorEmpleado; }
    public Integer getMinimoEnTurno() { return minimoEnTurno; }
    public void setMinimoEnTurno(Integer minimoEnTurno) { this.minimoEnTurno = minimoEnTurno; }
    public Integer getAntelacionHoras() { return antelacionHoras; }
    public void setAntelacionHoras(Integer antelacionHoras) { this.antelacionHoras = antelacionHoras; }
    public String getAlLlenarse() { return alLlenarse; }
    public void setAlLlenarse(String alLlenarse) { this.alLlenarse = alLlenarse; }
    public Boolean getPromoverEspera() { return promoverEspera; }
    public void setPromoverEspera(Boolean promoverEspera) { this.promoverEspera = promoverEspera; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public String getActualizadoPor() { return actualizadoPor; }
    public void setActualizadoPor(String actualizadoPor) { this.actualizadoPor = actualizadoPor; }
}
