package com.example.ControlNGR.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ControlNGR.dto.AsistenciaRequestDTO;
import com.example.ControlNGR.dto.AsistenciaResponseDTO;
import com.example.ControlNGR.dto.ReporteAsistenciaDTO;
import com.example.ControlNGR.entity.Asistencia;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.FeriadoLaborado;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.UsuarioActual;
import com.example.ControlNGR.entity.Horario;
import com.example.ControlNGR.entity.HorarioSemanalDetalle;
import com.example.ControlNGR.repository.AsistenciaRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.HorarioRepository;
import com.example.ControlNGR.repository.HorarioSemanalDetalleRepository;
import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AsistenciaService {
    
    @Autowired
    private AsistenciaRepository asistenciaRepository;
    
    @Autowired
    private EmpleadoRepository empleadoRepository;
    
    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private HorarioSemanalDetalleRepository horarioSemanalDetalleRepository;

    @Autowired
    private UsuarioActual usuarioActual;

    @Autowired
    private RedService redService;

    @Autowired
    private ParametroService parametroService;

    @Autowired
    private FeriadoLaboradoService feriadoLaboradoService;

    /**
     * Registrar entrada o salida del usuario autenticado.
     * <ul>
     *   <li>La fecha y hora las pone el servidor (no el cliente).</li>
     *   <li>Solo se permite desde los segmentos de red registrados en el panel admin.</li>
     *   <li>La entrada solo se permite si el dia esta programado como laboral en su horario.</li>
     *   <li>Si el dia es feriado, se abonan automaticamente los dias de compensacion.</li>
     * </ul>
     */
    @Transactional
    public AsistenciaResponseDTO registrarAsistencia(AsistenciaRequestDTO request, String ipCliente) {
        Empleado empleado = usuarioActual.empleadoRequerido();
        if (request.getEmpleadoId() != null && !request.getEmpleadoId().equals(empleado.getId())) {
            throw new AccesoDenegadoException("Solo puede marcar su propia asistencia");
        }
        if (!Boolean.TRUE.equals(empleado.getUsuario().getTipoUsuario().getMarcaAsistencia())) {
            throw new AccesoDenegadoException("Su rol no registra asistencia");
        }
        redService.validarIp(ipCliente);

        LocalDateTime ahora = LocalDateTime.now();
        LocalDate fecha = ahora.toLocalDate();
        LocalTime hora = ahora.toLocalTime().withNano(0);

        Asistencia asistencia;
        FeriadoLaborado feriadoLaborado = null;

        if ("entrada".equalsIgnoreCase(request.getTipo())) {
            asistencia = asistenciaRepository.findByEmpleadoAndFecha(empleado, fecha)
                    .orElseGet(() -> new Asistencia(empleado, fecha));
            if (asistencia.getHoraEntrada() != null) {
                throw new RuntimeException("Ya se registró entrada para hoy");
            }

            LocalTime horaEntradaProgramada = obtenerEntradaProgramada(empleado, fecha);
            asistencia.setHoraEntrada(hora);
            asistencia.setIpEntrada(ipCliente);

            int tolerancia = parametroService.entero(ParametroService.TOLERANCIA_TARDANZA_MINUTOS, 5);
            if (hora.isAfter(horaEntradaProgramada.plusMinutes(tolerancia))) {
                asistencia.setEstado("tardanza");
                asistencia.setObservaciones("Marcaje tarde");
            } else {
                asistencia.setEstado("presente");
            }
            agregarObservacion(asistencia, request.getObservaciones());
            asistencia = asistenciaRepository.save(asistencia);

            feriadoLaborado = feriadoLaboradoService.registrarSiEsFeriado(asistencia).orElse(null);

        } else if ("salida".equalsIgnoreCase(request.getTipo())) {
            // Turnos que cruzan la medianoche: si hoy no hay entrada, se cierra la de ayer
            asistencia = asistenciaRepository.findByEmpleadoAndFecha(empleado, fecha)
                    .filter(a -> a.getHoraEntrada() != null)
                    .or(() -> asistenciaRepository.findByEmpleadoAndFecha(empleado, fecha.minusDays(1))
                            .filter(a -> a.getHoraEntrada() != null && a.getHoraSalida() == null))
                    .orElseThrow(() -> new RuntimeException("Debe registrar entrada primero"));
            if (asistencia.getHoraSalida() != null) {
                throw new RuntimeException("Ya se registró salida para hoy");
            }
            asistencia.setHoraSalida(hora);
            asistencia.setIpSalida(ipCliente);
            asistencia.setSalidaAutomatica(false);
            agregarObservacion(asistencia, request.getObservaciones());
            asistencia = asistenciaRepository.save(asistencia);
        } else {
            throw new RuntimeException("Tipo de registro inválido. Use 'entrada' o 'salida'");
        }

        AsistenciaResponseDTO dto = new AsistenciaResponseDTO(asistencia);
        if (feriadoLaborado != null) {
            dto.setFeriado(feriadoLaborado.getFeriado().getDescripcion());
            dto.setDiasCompensacionAbonados(feriadoLaborado.getDiasOtorgados());
        }
        return dto;
    }

    /**
     * Hora de entrada del turno programado para la fecha. Prioridad: horario semanal activo > horario base.
     * Si el dia no es laboral (descanso, vacaciones, etc.) o no hay horario, no se permite marcar.
     */
    private LocalTime obtenerEntradaProgramada(Empleado empleado, LocalDate fecha) {
        String tipoDia;
        LocalTime entrada;
        Optional<HorarioSemanalDetalle> semanal =
                horarioSemanalDetalleRepository.findHorarioActivoEmpleadoEnFecha(empleado.getId(), fecha);
        if (semanal.isPresent()) {
            tipoDia = semanal.get().getTipoDia();
            entrada = semanal.get().getHoraEntrada();
        } else {
            Optional<Horario> base = horarioRepository.findByEmpleadoIdAndDiaSemana(
                    empleado.getId(), traducirDia(fecha.getDayOfWeek().toString().toLowerCase()));
            tipoDia = base.map(Horario::getTipoDia).orElse(null);
            entrada = base.map(Horario::getHoraEntrada).orElse(null);
        }
        boolean laboral = tipoDia == null || "normal".equalsIgnoreCase(tipoDia);
        if (!laboral || entrada == null) {
            throw new IllegalStateException("No tiene un turno laboral programado para hoy ("
                    + fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + (tipoDia != null && !laboral ? ", día de " + tipoDia : "")
                    + "). No puede marcar asistencia.");
        }
        return entrada;
    }

    private void agregarObservacion(Asistencia asistencia, String observacion) {
        if (observacion == null || observacion.trim().isEmpty()) return;
        String actuales = asistencia.getObservaciones();
        asistencia.setObservaciones(actuales == null ? observacion.trim() : actuales + " | " + observacion.trim());
    }

    // TRADUCTOR DE DIAS
    private String traducirDia(String diaIngles) {
        switch(diaIngles) {
            case "monday": return "lunes";
            case "tuesday": return "martes";
            case "wednesday": return "miercoles";
            case "thursday": return "jueves";
            case "friday": return "viernes";
            case "saturday": return "sabado";
            case "sunday": return "domingo";
            default: return diaIngles;
        }
    }

    // MÉTODOS DE LECTURA (IMPORTANTE: @Transactional readOnly = true para evitar error 500)
    
    @Transactional(readOnly = true)
    public List<AsistenciaResponseDTO> obtenerTodasAsistencias() {
        return asistenciaRepository.findAll().stream()
                .map(AsistenciaResponseDTO::new)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AsistenciaResponseDTO> obtenerAsistenciasPorEmpleado(Integer empleadoId) {
        return asistenciaRepository.findByEmpleadoId(empleadoId).stream()
                .map(AsistenciaResponseDTO::new)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AsistenciaResponseDTO> obtenerAsistenciasPorFecha(LocalDate fecha) {
        return asistenciaRepository.findByFecha(fecha).stream()
                .map(AsistenciaResponseDTO::new)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AsistenciaResponseDTO> obtenerAsistenciasPorRango(LocalDate inicio, LocalDate fin) {
        return asistenciaRepository.findByFechaBetween(inicio, fin).stream()
                .map(AsistenciaResponseDTO::new)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AsistenciaResponseDTO> obtenerReporteMensual(Integer empleadoId, int year, int month) {
        return asistenciaRepository.findByEmpleadoAndMonthYear(empleadoId, year, month).stream()
                .map(AsistenciaResponseDTO::new)
                .collect(Collectors.toList());
    }
    
    // Generar reporte de asistencia con calculo de puntualidad
    // Prioriza horarios semanales si existen, sino usa horario base
    @Transactional(readOnly = true)
    public List<ReporteAsistenciaDTO> generarReporteAsistencia(LocalDate fechaInicio, LocalDate fechaFin) {
        List<ReporteAsistenciaDTO> reporte = new ArrayList<>();

        // Obtener empleados activos (excluyendo admin)
        List<Empleado> empleados = empleadoRepository.findEmpleadosConHorario();

        // Obtener todas las asistencias en el rango
        List<Asistencia> asistencias = asistenciaRepository.findByFechaBetween(fechaInicio, fechaFin);

        for (Empleado empleado : empleados) {
            // Iterar cada dia del rango
            LocalDate fecha = fechaInicio;
            while (!fecha.isAfter(fechaFin)) {
                String diaSemanaIngles = fecha.getDayOfWeek().toString().toLowerCase();
                String diaSemanaEsp = traducirDia(diaSemanaIngles);

                // Solo usar horarios semanales activos (no borradores ni historicos)
                Optional<HorarioSemanalDetalle> horarioSemanalOpt =
                        horarioSemanalDetalleRepository.findHorarioActivoEmpleadoEnFecha(empleado.getId(), fecha);

                // Variables para el horario a usar
                LocalTime horaEntradaProgramada = null;
                LocalTime horaSalidaProgramada = null;
                String tipoDia = null;
                String turno = null;
                boolean tieneHorario = false;

                if (horarioSemanalOpt.isPresent()) {
                    HorarioSemanalDetalle detalle = horarioSemanalOpt.get();
                    horaEntradaProgramada = detalle.getHoraEntrada();
                    horaSalidaProgramada = detalle.getHoraSalida();
                    tipoDia = detalle.getTipoDia();
                    turno = detalle.getTurno();
                    tieneHorario = true;
                }

                // Buscar asistencia para este empleado en esta fecha
                Asistencia asistencia = null;
                for (Asistencia a : asistencias) {
                    if (a.getEmpleado().getId().equals(empleado.getId()) && a.getFecha().equals(fecha)) {
                        asistencia = a;
                        break;
                    }
                }

                ReporteAsistenciaDTO dto = new ReporteAsistenciaDTO();
                dto.setEmpleadoId(empleado.getId());
                dto.setEmpleadoNombre(empleado.getNombre());
                dto.setEmpleadoRol(empleado.getRol());
                dto.setEmpleadoCargo(empleado.getCargo());
                dto.setFecha(fecha);
                dto.setDiaSemana(diaSemanaEsp);

                if (tieneHorario) {
                    dto.setTipoDia(tipoDia);
                    dto.setTurno(turno);
                    dto.setHorarioEntradaFromTime(horaEntradaProgramada);
                    dto.setHorarioSalidaFromTime(horaSalidaProgramada);
                }

                if (tieneHorario && tipoDia != null && !"normal".equalsIgnoreCase(tipoDia)) {
                    // Dia no laboral (descanso, compensado, vacaciones, permiso)
                    String tipoFormateado = tipoDia.substring(0, 1).toUpperCase()
                            + tipoDia.substring(1).toLowerCase();
                    dto.setEstado(tipoFormateado);
                    dto.setMinutosRetraso(null);
                } else if (!tieneHorario) {
                    // Sin horario definido
                    dto.setEstado("Sin horario");
                    dto.setMinutosRetraso(null);
                } else {
                    // Dia laboral normal
                    if (asistencia != null && asistencia.getHoraEntrada() != null) {
                        dto.setHoraEntradaRealFromTime(asistencia.getHoraEntrada());
                        dto.setHoraSalidaRealFromTime(asistencia.getHoraSalida());
                        dto.setObservaciones(asistencia.getObservaciones());
                        dto.setSalidaAutomatica(asistencia.getSalidaAutomatica());

                        // Calcular retraso
                        if (horaEntradaProgramada != null) {
                            long minutos = Duration.between(
                                    horaEntradaProgramada, asistencia.getHoraEntrada()
                            ).toMinutes();

                            if (minutos > 5) {
                                dto.setEstado("Tardanza");
                                dto.setMinutosRetraso(minutos);
                            } else {
                                dto.setEstado("A tiempo");
                                dto.setMinutosRetraso(0L);
                            }
                        } else {
                            dto.setEstado("A tiempo");
                            dto.setMinutosRetraso(0L);
                        }
                    } else if (asistencia != null && "permiso".equalsIgnoreCase(asistencia.getEstado())) {
                        dto.setEstado("Permiso");
                        dto.setObservaciones(asistencia.getObservaciones());
                        dto.setMinutosRetraso(null);
                    } else {
                        // Sin asistencia en dia laboral
                        if (!fecha.isAfter(LocalDate.now())) {
                            dto.setEstado("Falta");
                        } else {
                            dto.setEstado("Pendiente");
                        }
                        dto.setMinutosRetraso(null);
                    }
                }

                reporte.add(dto);
                fecha = fecha.plusDays(1);
            }
        }

        return reporte;
    }

    // Verificar y marcar salidas automáticas
    @Transactional
    public void verificarSalidasAutomaticas() {
        List<Asistencia> asistenciasPendientes = asistenciaRepository.findAsistenciasConSalidaPendiente();
        LocalDateTime ahora = LocalDateTime.now();
        
        for (Asistencia asistencia : asistenciasPendientes) {
            LocalDateTime horaEntrada = LocalDateTime.of(asistencia.getFecha(), asistencia.getHoraEntrada());
            long horasTranscurridas = Duration.between(horaEntrada, ahora).toHours();
            
            if (horasTranscurridas >= 12) {
                LocalTime horaSalidaCalculada = asistencia.getHoraEntrada()
                        .plusHours(9); // 8h trabajo + 1h refrigerio
                
                asistencia.setHoraSalida(horaSalidaCalculada);
                asistencia.setSalidaAutomatica(true);
                
                String observacion = "Salida automática por sistema.";
                String obsActual = asistencia.getObservaciones();
                asistencia.setObservaciones(obsActual == null ? observacion : obsActual + " " + observacion);
                
                asistenciaRepository.save(asistencia);
            }
        }
    }
}