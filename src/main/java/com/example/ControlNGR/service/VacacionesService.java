package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.MovimientoSaldo.Origen;
import com.example.ControlNGR.entity.MovimientoSaldo.TipoMovimiento;
import com.example.ControlNGR.entity.PeriodoVacacional;
import com.example.ControlNGR.entity.TipoSaldo;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.PeriodoVacacionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Abona 30 dias de vacaciones (parametro DIAS_VACACIONES_POR_ANIO) cada vez que
 * un empleado cumple un año desde su fecha de ingreso.
 * Solo se abonan automaticamente los aniversarios a partir de VACACIONES_ABONO_DESDE;
 * los dias acumulados antes de esa fecha se registran con la carga inicial del admin.
 */
@Service
public class VacacionesService {

    private static final Logger logger = LoggerFactory.getLogger(VacacionesService.class);

    private final EmpleadoRepository empleadoRepository;
    private final PeriodoVacacionalRepository periodoRepository;
    private final SaldoService saldoService;
    private final ParametroService parametroService;
    private final TransactionTemplate transaccion;

    public VacacionesService(EmpleadoRepository empleadoRepository,
                             PeriodoVacacionalRepository periodoRepository,
                             SaldoService saldoService,
                             ParametroService parametroService,
                             PlatformTransactionManager transactionManager) {
        this.empleadoRepository = empleadoRepository;
        this.periodoRepository = periodoRepository;
        this.saldoService = saldoService;
        this.parametroService = parametroService;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        ejecutarProceso();
    }

    /** Todos los dias a las 00:15 (hora de Lima). */
    @Scheduled(cron = "0 15 0 * * *", zone = "America/Lima")
    public void programado() {
        ejecutarProceso();
    }

    private void ejecutarProceso() {
        try {
            // Llamada interna: se abre la transaccion explicitamente (el proxy de @Transactional no aplica)
            Integer abonados = transaccion.execute(status -> procesarAniversarios(LocalDate.now()));
            if (abonados != null && abonados > 0) {
                logger.info("Proceso de vacaciones: {} periodo(s) abonado(s)", abonados);
            }
        } catch (Exception e) {
            logger.error("Error en el proceso de vacaciones: {}", e.getMessage(), e);
        }
    }

    /** Genera los periodos cumplidos hasta la fecha indicada. Devuelve cuantos se abonaron. */
    @Transactional
    public int procesarAniversarios(LocalDate hoy) {
        LocalDate abonoDesde = parametroService.fecha(ParametroService.VACACIONES_ABONO_DESDE, hoy);
        BigDecimal diasPorAnio = parametroService.decimal(ParametroService.DIAS_VACACIONES_POR_ANIO, BigDecimal.valueOf(30));
        int total = 0;

        List<Empleado> empleados = empleadoRepository.findByActivo(true);
        for (Empleado empleado : empleados) {
            if (empleado.getIngreso() == null || empleado.getUsuario() == null
                    || !Boolean.TRUE.equals(empleado.getUsuario().getTipoUsuario().getPuedeSolicitar())) {
                continue;
            }
            LocalDate inicio = empleado.getIngreso();
            LocalDate aniversario = inicio.plusYears(1);
            while (!aniversario.isAfter(hoy)) {
                if (!aniversario.isBefore(abonoDesde)
                        && !periodoRepository.existsByEmpleadoIdAndPeriodoInicio(empleado.getId(), inicio)) {
                    PeriodoVacacional periodo = new PeriodoVacacional();
                    periodo.setEmpleado(empleado);
                    periodo.setPeriodoInicio(inicio);
                    periodo.setPeriodoFin(aniversario.minusDays(1));
                    periodo.setFechaAdquisicion(aniversario);
                    periodo.setDiasGanados(diasPorAnio);
                    periodo = periodoRepository.save(periodo);

                    saldoService.registrar(empleado, TipoSaldo.VACACIONES, TipoMovimiento.ABONO, diasPorAnio,
                            Origen.PERIODO_VACACIONAL,
                            "Periodo " + inicio.getYear() + "-" + aniversario.getYear() + " cumplido el " + aniversario,
                            null, null, null, periodo, false);
                    total++;
                }
                inicio = aniversario;
                aniversario = aniversario.plusYears(1);
            }
        }
        return total;
    }

    @Transactional(readOnly = true)
    public List<PeriodoVacacional> periodos(Integer empleadoId) {
        return periodoRepository.findByEmpleadoIdOrderByPeriodoInicioDesc(empleadoId);
    }
}
