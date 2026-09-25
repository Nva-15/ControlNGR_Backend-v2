package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.entity.MovimientoSaldo.Origen;
import com.example.ControlNGR.entity.MovimientoSaldo.TipoMovimiento;
import com.example.ControlNGR.repository.DiaFeriadoRepository;
import com.example.ControlNGR.repository.FeriadoLaboradoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Abono automatico de dias de compensacion por trabajar en feriado. */
@Service
public class FeriadoLaboradoService {

    private static final Logger logger = LoggerFactory.getLogger(FeriadoLaboradoService.class);

    private final DiaFeriadoRepository feriadoRepository;
    private final FeriadoLaboradoRepository feriadoLaboradoRepository;
    private final SaldoService saldoService;
    private final ParametroService parametroService;

    public FeriadoLaboradoService(DiaFeriadoRepository feriadoRepository,
                                  FeriadoLaboradoRepository feriadoLaboradoRepository,
                                  SaldoService saldoService,
                                  ParametroService parametroService) {
        this.feriadoRepository = feriadoRepository;
        this.feriadoLaboradoRepository = feriadoLaboradoRepository;
        this.saldoService = saldoService;
        this.parametroService = parametroService;
    }

    /**
     * Se llama al registrar la entrada. Si la fecha es feriado, abona los dias configurados
     * (por defecto 2). Solo una vez por empleado y feriado.
     */
    @Transactional
    public Optional<FeriadoLaborado> registrarSiEsFeriado(Asistencia asistencia) {
        Empleado empleado = asistencia.getEmpleado();
        Optional<DiaFeriado> feriadoOpt = feriadoRepository.findByFechaAndActivoTrue(asistencia.getFecha());
        if (feriadoOpt.isEmpty()) {
            return Optional.empty();
        }
        DiaFeriado feriado = feriadoOpt.get();
        if (feriadoLaboradoRepository.existsByEmpleadoIdAndFeriadoId(empleado.getId(), feriado.getId())) {
            return Optional.empty();
        }

        BigDecimal dias = parametroService.decimal(ParametroService.DIAS_POR_FERIADO_LABORADO, BigDecimal.valueOf(2));

        FeriadoLaborado fl = new FeriadoLaborado();
        fl.setEmpleado(empleado);
        fl.setFeriado(feriado);
        fl.setAsistencia(asistencia);
        fl.setFecha(asistencia.getFecha());
        fl.setDiasOtorgados(dias);
        fl.setEstado(FeriadoLaborado.ABONADO);
        fl = feriadoLaboradoRepository.save(fl);

        saldoService.registrar(empleado, TipoSaldo.COMPENSACION, TipoMovimiento.ABONO, dias,
                Origen.FERIADO_LABORADO, "Feriado laborado: " + feriado.getDescripcion() + " (" + feriado.getFecha() + ")",
                null, null, fl, null, false);

        logger.info("Feriado laborado registrado: empleado {} fecha {} (+{} dias)", empleado.getId(), fl.getFecha(), dias);
        return Optional.of(fl);
    }

    /** Anula un feriado laborado desde el panel admin y descuenta los dias abonados. */
    @Transactional
    public FeriadoLaborado revertir(Integer feriadoLaboradoId, String motivo, Usuario usuario) {
        FeriadoLaborado fl = feriadoLaboradoRepository.findById(feriadoLaboradoId)
                .orElseThrow(() -> new IllegalArgumentException("Feriado laborado no encontrado"));
        if (FeriadoLaborado.REVERTIDO.equals(fl.getEstado())) {
            throw new IllegalStateException("El feriado laborado ya fue revertido");
        }
        fl.setEstado(FeriadoLaborado.REVERTIDO);
        fl.setObservacion(motivo);
        feriadoLaboradoRepository.save(fl);

        saldoService.registrar(fl.getEmpleado(), TipoSaldo.COMPENSACION, TipoMovimiento.REVERSION,
                fl.getDiasOtorgados().negate(), Origen.FERIADO_LABORADO,
                "Reversion de feriado laborado " + fl.getFecha() + (motivo != null ? ": " + motivo : ""),
                usuario, null, fl, null, true);
        return fl;
    }

    @Transactional(readOnly = true)
    public List<FeriadoLaborado> listar(Integer empleadoId) {
        return empleadoId != null
                ? feriadoLaboradoRepository.findByEmpleadoIdOrderByFechaDesc(empleadoId)
                : feriadoLaboradoRepository.findAllByOrderByFechaDesc();
    }
}
