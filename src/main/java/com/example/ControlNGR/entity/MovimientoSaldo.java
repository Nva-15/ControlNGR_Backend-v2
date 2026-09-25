package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Movimiento inmutable que suma o resta dias a un saldo. */
@Entity
@Table(name = "movimientos_saldo")
public class MovimientoSaldo {

    public enum TipoMovimiento { ABONO, CARGO, REVERSION, AJUSTE, CARGA_INICIAL }

    public enum Origen { FERIADO_LABORADO, PERIODO_VACACIONAL, SOLICITUD, PANEL_ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_saldo", nullable = false, length = 20)
    private TipoSaldo tipoSaldo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 20)
    private TipoMovimiento tipoMovimiento;

    @Column(name = "dias", nullable = false, precision = 6, scale = 1)
    private BigDecimal dias;

    @Column(name = "saldo_resultante", nullable = false, precision = 6, scale = 1)
    private BigDecimal saldoResultante;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 30)
    private Origen origen;

    @ManyToOne
    @JoinColumn(name = "feriado_laborado_id")
    private FeriadoLaborado feriadoLaborado;

    @ManyToOne
    @JoinColumn(name = "periodo_vacacional_id")
    private PeriodoVacacional periodoVacacional;

    @ManyToOne
    @JoinColumn(name = "solicitud_id")
    private Solicitud solicitud;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Integer getId() { return id; }

    public Empleado getEmpleado() { return empleado; }
    public void setEmpleado(Empleado empleado) { this.empleado = empleado; }

    public TipoSaldo getTipoSaldo() { return tipoSaldo; }
    public void setTipoSaldo(TipoSaldo tipoSaldo) { this.tipoSaldo = tipoSaldo; }

    public TipoMovimiento getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(TipoMovimiento tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public BigDecimal getDias() { return dias; }
    public void setDias(BigDecimal dias) { this.dias = dias; }

    public BigDecimal getSaldoResultante() { return saldoResultante; }
    public void setSaldoResultante(BigDecimal saldoResultante) { this.saldoResultante = saldoResultante; }

    public Origen getOrigen() { return origen; }
    public void setOrigen(Origen origen) { this.origen = origen; }

    public FeriadoLaborado getFeriadoLaborado() { return feriadoLaborado; }
    public void setFeriadoLaborado(FeriadoLaborado feriadoLaborado) { this.feriadoLaborado = feriadoLaborado; }

    public PeriodoVacacional getPeriodoVacacional() { return periodoVacacional; }
    public void setPeriodoVacacional(PeriodoVacacional periodoVacacional) { this.periodoVacacional = periodoVacacional; }

    public Solicitud getSolicitud() { return solicitud; }
    public void setSolicitud(Solicitud solicitud) { this.solicitud = solicitud; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
