package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Saldo actual de dias de un empleado. Solo lo modifica SaldoService junto con un movimiento. */
@Entity
@Table(name = "saldos")
public class Saldo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_saldo", nullable = false, length = 20)
    private TipoSaldo tipoSaldo;

    @Column(name = "saldo", nullable = false, precision = 6, scale = 1)
    private BigDecimal saldo = BigDecimal.ZERO;

    public Saldo() {}

    public Saldo(Empleado empleado, TipoSaldo tipoSaldo) {
        this.empleado = empleado;
        this.tipoSaldo = tipoSaldo;
    }

    public Integer getId() { return id; }
    public Empleado getEmpleado() { return empleado; }
    public TipoSaldo getTipoSaldo() { return tipoSaldo; }

    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
}
