package com.example.ControlNGR.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ControlNGR.dto.AsistenciaRequestDTO;
import com.example.ControlNGR.dto.AsistenciaResponseDTO;
import com.example.ControlNGR.dto.ReporteAsistenciaDTO;
import com.example.ControlNGR.entity.Asistencia;
import com.example.ControlNGR.entity.Empleado;
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

    // Registrar entrada o salida
    @Transactional
    public AsistenciaResponseDTO registrarAsistencia(AsistenciaRequestDTO request) {
        if (request.getEmpleadoId() == null) {
            throw new RuntimeException("ID de empleado requerido");
        }

        Optional<Empleado> empleadoOpt = empleadoRepository.findById(request.getEmpleadoId());
        if (!empleadoOpt.isPresent()) {
            throw new RuntimeException("Empleado no encontrado");
        }
        
        Empleado empleado = empleadoOpt.get();
        LocalDate fecha = (request.getFecha() != null) ? request.getFecha() : LocalDate.now();
        LocalTime hora = (request.getHora() != null) ? request.getHora() : LocalTime.now();
        
        // Buscar o crear registro de asistencia para el día
        Optional<Asistencia> asistenciaOpt = asistenciaRepository.findByEmpleadoAndFecha(empleado, fecha);
        Asistencia asistencia;
        
        if (asistenciaOpt.isPresent()) {
            asistencia = asistenciaOpt.get();
        } else {
            asistencia = new Asistencia(empleado, fecha);
        }
        
        // Determinar si es entrada o salida
        if ("entrada".equalsIgnoreCase(request.getTipo())) {
            // Registrar entrada
            if (asistencia.getHoraEntrada() != null) {
                throw new RuntimeException("Ya se registró entrada para hoy");
            }
            
            asistencia.setHoraEntrada(hora);

            // Verificar si es tardanza
            // Prioridad: horario semanal > horario base
            String diaSemana = fecha.getDayOfWeek().toString().toLowerCase();
            String diaEsp = traducirDia(diaSemana);

            LocalTime horaEntradaProgramada = null;

            // Buscar primero en horario semanal activo
            Optional<HorarioSemanalDetalle> horarioSemanalOpt =
                    horarioSemanalDetalleRepository.findHorarioActivoEmpleadoEnFecha(empleado.getId(), fecha);

            if (horarioSemanalOpt.isPresent()) {
                HorarioSemanalDetalle detalle = horarioSemanalOpt.get();
                horaEntradaProgramada = detalle.getHoraEntrada();
            } else {
                // Fallback: horario base
                Optional<Horario> horarioOpt = horarioRepository.findByEmpleadoIdAndDiaSemana(
                        empleado.getId(), diaEsp);
                if (horarioOpt.isPresent()) {
                    horaEntradaProgramada = horarioOpt.get().getHoraEntrada();
                }
            }

            if (horaEntradaProgramada != null) {
                if (hora.isAfter(horaEntradaProgramada.plusMinutes(5))) { // Tolerancia 5 min
                    asistencia.setEstado("tardanza");
                    asistencia.setObservaciones("Marcaje tarde");
                } else {
                    asistencia.setEstado("presente");
                }
            } else {
                asistencia.setEstado("presente");
            }
            
        } else if ("salida".equalsIgnoreCase(request.getTipo())) {
            // Registrar salida
            if (asistencia.getHoraEntrada() == null) {
                throw new RuntimeException("Debe registrar entrada primero");
            }
            if (asistencia.getHoraSalida() != null) {
                throw new RuntimeException("Ya se registró salida para hoy");
            }
            
            asistencia.setHoraSalida(hora);
            asistencia.setSalidaAutomatica(false);
            
        } else {
            throw new RuntimeException("Tipo de registro inválido. Use 'entrada' o 'salida'");
        }
        
        // Guardar observaciones si existen
        if (request.getObservaciones() != null && !request.getObservaciones().trim().isEmpty()) {
            String observacionesActuales = asistencia.getObservaciones();
            if (observacionesActuales == null) {
                asistencia.setObservaciones(request.getObservaciones());
            } else {
                asistencia.setObservaciones(observacionesActuales + " | " + request.getObservaciones());
            }
        }
        
        Asistencia asistenciaGuardada = asistenciaRepository.save(asistencia);
        return new AsistenciaResponseDTO(asistenciaGuardada);
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