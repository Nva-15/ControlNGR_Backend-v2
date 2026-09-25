package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.entity.MovimientoSaldo.Origen;
import com.example.ControlNGR.entity.MovimientoSaldo.TipoMovimiento;
import com.example.ControlNGR.repository.MovimientoSaldoRepository;
import com.example.ControlNGR.repository.SaldoRepository;
import com.example.ControlNGR.repository.SolicitudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Unico punto que modifica saldos. Cada cambio queda registrado como un
 * movimiento con el saldo resultante, de modo que el historial siempre cuadra.
 */
@Service
public class SaldoService {

    private final SaldoRepository saldoRepository;
    private final MovimientoSaldoRepository movimientoRepository;
    private final SolicitudRepository solicitudRepository;

    public SaldoService(SaldoRepository saldoRepository,
                        MovimientoSaldoRepository movimientoRepository,
                        SolicitudRepository solicitudRepository) {
        this.saldoRepository = saldoRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudRepository = solicitudRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal saldoActual(Integer empleadoId, TipoSaldo tipo) {
        return saldoRepository.findByEmpleadoIdAndTipoSaldo(empleadoId, tipo)
                .map(Saldo::getSaldo)
                .orElse(BigDecimal.ZERO);
    }

    /** Dias comprometidos en solicitudes pendientes del mismo tipo de saldo. */
    @Transactional(readOnly = true)
    public BigDecimal diasPendientes(Integer empleadoId, TipoSaldo tipo, Integer excluirSolicitudId) {
        BigDecimal suma = solicitudRepository.sumarDiasPendientes(empleadoId, tipo.name(), excluirSolicitudId);
        return suma != null ? suma : BigDecimal.ZERO;
    }

    /** Saldo menos lo comprometido en solicitudes pendientes. */
    @Transactional(readOnly = true)
    public BigDecimal disponible(Integer empleadoId, TipoSaldo tipo, Integer excluirSolicitudId) {
        return saldoActual(empleadoId, tipo).subtract(diasPendientes(empleadoId, tipo, excluirSolicitudId));
    }

    /**
     * Registra un movimiento y actualiza el saldo dentro de la transaccion actual.
     * La fila del saldo se bloquea para evitar descuentos simultaneos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoSaldo registrar(Empleado empleado, TipoSaldo tipo, TipoMovimiento tipoMovimiento,
                                     BigDecimal dias, Origen origen, String observacion, Usuario usuario,
                                     Solicitud solicitud, FeriadoLaborado feriado, PeriodoVacacional periodo,
                                     boolean permitirNegativo) {
        Saldo saldo = saldoRepository.bloquear(empleado.getId(), tipo)
                .orElseGet(() -> saldoRepository.saveAndFlush(new Saldo(empleado, tipo)));

        BigDecimal nuevo = saldo.getSaldo().add(dias);
        if (!permitirNegativo && nuevo.signum() < 0) {
            throw new IllegalStateException("Saldo insuficiente de " + nombre(tipo) + ". Disponible: "
                    + saldo.getSaldo().stripTrailingZeros().toPlainString() + " dia(s), requerido: "
                    + dias.negate().stripTrailingZeros().toPlainString() + " dia(s).");
        }
        saldo.setSaldo(nuevo);
        saldoRepository.save(saldo);

        MovimientoSaldo mov = new MovimientoSaldo();
        mov.setEmpleado(empleado);
        mov.setTipoSaldo(tipo);
        mov.setTipoMovimiento(tipoMovimiento);
        mov.setDias(dias);
        mov.setSaldoResultante(nuevo);
        mov.setOrigen(origen);
        mov.setObservacion(recortar(observacion));
        mov.setUsuario(usuario);
        mov.setSolicitud(solicitud);
        mov.setFeriadoLaborado(feriado);
        mov.setPeriodoVacacional(periodo);
        return movimientoRepository.save(mov);
    }

    /**
     * Carga inicial / correccion desde el panel admin: deja el saldo exactamente en el valor indicado.
     * Registra la diferencia como movimiento para conservar el historial.
     */
    @Transactional
    public MovimientoSaldo establecerSaldo(Empleado empleado, TipoSaldo tipo, BigDecimal nuevoSaldo,
                                           TipoMovimiento tipoMovimiento, String observacion, Usuario usuario) {
        if (nuevoSaldo == null || nuevoSaldo.signum() < 0) {
            throw new IllegalArgumentException("El saldo debe ser un numero mayor o igual a 0");
        }
        BigDecimal actual = saldoRepository.bloquear(empleado.getId(), tipo)
                .map(Saldo::getSaldo).orElse(BigDecimal.ZERO);
        BigDecimal diferencia = nuevoSaldo.subtract(actual);
        return registrar(empleado, tipo, tipoMovimiento, diferencia, Origen.PANEL_ADMIN,
                observacion, usuario, null, null, null, true);
    }

    @Transactional(readOnly = true)
    public List<MovimientoSaldo> movimientos(Integer empleadoId, TipoSaldo tipo) {
        return tipo == null
                ? movimientoRepository.findByEmpleadoIdOrderByCreatedAtDescIdDesc(empleadoId)
                : movimientoRepository.findByEmpleadoIdAndTipoSaldoOrderByCreatedAtDescIdDesc(empleadoId, tipo);
    }

    /** Resumen de ambos saldos de un empleado. */
    @Transactional(readOnly = true)
    public java.util.Map<String, com.example.ControlNGR.dto.SaldoResumenDTO> resumen(Integer empleadoId) {
        java.util.Map<String, com.example.ControlNGR.dto.SaldoResumenDTO> r = new java.util.LinkedHashMap<>();
        for (TipoSaldo tipo : TipoSaldo.values()) {
            BigDecimal saldo = saldoActual(empleadoId, tipo);
            BigDecimal pendiente = diasPendientes(empleadoId, tipo, null);
            r.put(tipo.name().toLowerCase(), new com.example.ControlNGR.dto.SaldoResumenDTO(
                    tipo.name(), saldo, pendiente, saldo.subtract(pendiente)));
        }
        return r;
    }

    public static String nombre(TipoSaldo tipo) {
        return tipo == TipoSaldo.VACACIONES ? "vacaciones" : "dias por compensar";
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() > 255 ? texto.substring(0, 255) : texto;
    }
}
