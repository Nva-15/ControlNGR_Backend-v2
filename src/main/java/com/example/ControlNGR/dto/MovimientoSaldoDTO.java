package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.MovimientoSaldo;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoSaldoDTO(Integer id, Integer empleadoId, String tipoSaldo, String tipoMovimiento,
                                 BigDecimal dias, BigDecimal saldoResultante, String origen, String observacion,
                                 Integer solicitudId, Integer feriadoLaboradoId, Integer periodoVacacionalId,
                                 String registradoPor, LocalDateTime fecha) {

    public static MovimientoSaldoDTO de(MovimientoSaldo m) {
        String por = m.getUsuario() == null ? "Sistema"
                : m.getUsuario().getEmpleado() != null ? m.getUsuario().getEmpleado().getNombre() : m.getUsuario().getUsername();
        return new MovimientoSaldoDTO(m.getId(), m.getEmpleado().getId(), m.getTipoSaldo().name(),
                m.getTipoMovimiento().name(), m.getDias(), m.getSaldoResultante(), m.getOrigen().name(),
                m.getObservacion(),
                m.getSolicitud() != null ? m.getSolicitud().getId() : null,
                m.getFeriadoLaborado() != null ? m.getFeriadoLaborado().getId() : null,
                m.getPeriodoVacacional() != null ? m.getPeriodoVacacional().getId() : null,
                por, m.getCreatedAt());
    }
}
