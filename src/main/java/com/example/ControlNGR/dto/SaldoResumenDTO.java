package com.example.ControlNGR.dto;

import java.math.BigDecimal;

/**
 * Resumen de un saldo: saldo actual, dias comprometidos en solicitudes pendientes
 * y lo que queda disponible para nuevas solicitudes.
 */
public record SaldoResumenDTO(String tipo, BigDecimal saldo, BigDecimal pendiente, BigDecimal disponible) {
}
