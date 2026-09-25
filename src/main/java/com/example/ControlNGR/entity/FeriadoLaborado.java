package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Registro de un feriado trabajado. Se crea al marcar la entrada en un feriado. */
@Entity
@Table(name = "feriados_laborados")
public class FeriadoLaborado {
    public static final String ABONADO = "ABONADO";
    public static final String REVERTIDO = "REVERTIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @ManyToOne(optional = false)
    @JoinColumn(name = "feriado_id", nullable = false)
    private DiaFeriado feriado;

    @ManyToOne
    @JoinColumn(name = "asistencia_id")
    private Asistencia asistencia;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "dias_otorgados", nullable = false, precision = 5, scale = 1)
    private BigDecimal diasOtorgados;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ABONADO;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public DiaFeriado getFeriado() { return feriado; }
    public void setFeriado(DiaFeriado feriado) { this.feriado = feriado; }

    public Asistencia getAsistencia() { return asistencia; }
    public void setAsistencia(Asistencia asistencia) { this.asistencia = asistencia; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public BigDecimal getDiasOtorgados() { return diasOtorgados; }
    public void setDiasOtorgados(BigDecimal diasOtorgados) { this.diasOtorgados = diasOtorgados; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
