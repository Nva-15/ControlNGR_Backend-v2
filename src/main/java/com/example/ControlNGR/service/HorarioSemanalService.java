package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.*;
import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HorarioSemanalService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HorarioSemanalService.class);

    @Autowired
    private HorarioSemanalRepository horarioSemanalRepository;

    @Autowired
    private HorarioSemanalDetalleRepository detalleRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private SolicitudRepository solicitudRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // ==================== CREAR SEMANA ====================

    @Transactional
    public HorarioSemanalResponseDTO generarSemana(HorarioSemanalRequestDTO request) {
        logger.info("Iniciando generación de semana: {} a {}", request.getFechaInicio(), request.getFechaFin());

        validarFechas(request.getFechaInicio(), request.getFechaFin());
        logger.debug("Fechas validadas correctamente");

        // Verificar que no exista una semana con las mismas fechas
        Optional<HorarioSemanal> existente = horarioSemanalRepository
                .findByFechaInicioAndFechaFin(request.getFechaInicio(), request.getFechaFin());
        if (existente.isPresent()) {
            throw new RuntimeException("Ya existe un horario semanal para estas fechas");
        }
        logger.debug("No existe semana duplicada");

        // Verificar solapamiento con otras semanas
        List<HorarioSemanal> solapadas = horarioSemanalRepository
                .findSemanasSolapadas(request.getFechaInicio(), request.getFechaFin());
        if (!solapadas.isEmpty()) {
            throw new RuntimeException("Las fechas se solapan con otra semana existente: " +
                    solapadas.get(0).getNombre());
        }
        logger.debug("No hay solapamiento con otras semanas");

        // El creador es opcional: el administrador del sistema no es empleado
        Empleado creadoPor = request.getCreadoPorId() == null ? null
                : empleadoRepository.findById(request.getCreadoPorId())
                    .orElseThrow(() -> new RuntimeException("Empleado creador no encontrado con ID: " + request.getCreadoPorId()));

        // Crear la semana
        HorarioSemanal semana = new HorarioSemanal(request.getFechaInicio(), request.getFechaFin(), creadoPor);
        logger.debug("Objeto semana creado");

        // Si hay una semana de origen para copiar
        if (request.getCopiarDeId() != null) {
            logger.info("Copiando de semana origen ID: {}", request.getCopiarDeId());
            HorarioSemanal semanaOrigen = horarioSemanalRepository.findById(request.getCopiarDeId())
                    .orElseThrow(() -> new RuntimeException("Semana de origen no encontrada"));
            semana = horarioSemanalRepository.save(semana);
            logger.debug("Semana guardada con ID: {}", semana.getId());
            copiarDetallesDeSemana(semanaOrigen, semana);
        } else {
            logger.info("Generando desde horario base");
            semana = horarioSemanalRepository.save(semana);
            logger.debug("Semana guardada con ID: {}", semana.getId());
            generarDetallesDesdeHorarioBase(semana);
        }

        // Aplicar solicitudes aprobadas que caigan en este rango
        logger.debug("Aplicando solicitudes aprobadas...");
        aplicarSolicitudesAprobadas(semana);

        logger.info("Construyendo response DTO...");
        HorarioSemanalResponseDTO response = construirResponseDTO(semana);
        logger.info("Semana generada exitosamente con ID: {}", response.getId());
        return response;
    }

    @Transactional
    public HorarioSemanalResponseDTO copiarSemana(Integer semanaOrigenId, LocalDate nuevaFechaInicio) {
        HorarioSemanal semanaOrigen = horarioSemanalRepository.findById(semanaOrigenId)
                .orElseThrow(() -> new RuntimeException("Semana de origen no encontrada"));

        // Calcular fecha fin (7 días después)
        LocalDate nuevaFechaFin = nuevaFechaInicio.plusDays(6);

        HorarioSemanalRequestDTO request = new HorarioSemanalRequestDTO();
        request.setFechaInicio(nuevaFechaInicio);
        request.setFechaFin(nuevaFechaFin);
        request.setCreadoPorId(semanaOrigen.getCreadoPor() != null ? semanaOrigen.getCreadoPor().getId() : null);
        request.setCopiarDeId(semanaOrigenId);

        return generarSemana(request);
    }

    // ==================== GENERAR DETALLES ====================

    private void generarDetallesDesdeHorarioBase(HorarioSemanal semana) {
        // Obtener empleados activos (sin admin)
        List<Empleado> empleados = empleadoRepository.findEmpleadosConHorario();

        if (empleados.isEmpty()) {
            throw new RuntimeException("No hay empleados activos para generar horarios. " +
                    "Asegúrese de que existan empleados con rol diferente a 'admin' y estado activo.");
        }

        LocalDate fecha = semana.getFechaInicio();
        while (!fecha.isAfter(semana.getFechaFin())) {
            String diaSemana = traducirDia(fecha.getDayOfWeek().toString().toLowerCase());

            for (Empleado empleado : empleados) {
                HorarioSemanalDetalle detalle = new HorarioSemanalDetalle(semana, empleado, fecha);

                // Buscar horario base para este día
                Optional<Horario> horarioBase = horarioRepository
                        .findByEmpleadoIdAndDiaSemana(empleado.getId(), diaSemana);

                if (horarioBase.isPresent()) {
                    detalle.copiarDesdeHorarioBase(horarioBase.get());
                } else {
                    detalle.setTipoDia("normal");
                    detalle.setOrigenTipoDia("manual");
                }

                detalleRepository.save(detalle);
            }

            fecha = fecha.plusDays(1);
        }
    }

    private void copiarDetallesDeSemana(HorarioSemanal origen, HorarioSemanal destino) {
        List<HorarioSemanalDetalle> detallesOrigen = detalleRepository.findByHorarioSemanalIdOrdenado(origen.getId());

        // Agrupar por empleado
        Map<Integer, List<HorarioSemanalDetalle>> porEmpleado = detallesOrigen.stream()
                .collect(Collectors.groupingBy(d -> d.getEmpleado().getId()));

        LocalDate fechaDestino = destino.getFechaInicio();
        int diaIndex = 0;

        while (!fechaDestino.isAfter(destino.getFechaFin())) {
            final int currentDiaIndex = diaIndex;
            final LocalDate currentFecha = fechaDestino;

            for (Map.Entry<Integer, List<HorarioSemanalDetalle>> entry : porEmpleado.entrySet()) {
                List<HorarioSemanalDetalle> detallesEmpleado = entry.getValue();

                // Saltar empleados inactivos al copiar
                if (!detallesEmpleado.isEmpty() && !Boolean.TRUE.equals(detallesEmpleado.get(0).getEmpleado().getActivo())) {
                    continue;
                }

                if (currentDiaIndex < detallesEmpleado.size()) {
                    HorarioSemanalDetalle detalleOrigen = detallesEmpleado.get(currentDiaIndex);

                    HorarioSemanalDetalle nuevoDetalle = new HorarioSemanalDetalle(
                            destino, detalleOrigen.getEmpleado(), currentFecha);

                    // Copiar datos (excepto solicitudes)
                    nuevoDetalle.setHoraEntrada(detalleOrigen.getHoraEntrada());
                    nuevoDetalle.setHoraSalida(detalleOrigen.getHoraSalida());
                    nuevoDetalle.setHoraAlmuerzoInicio(detalleOrigen.getHoraAlmuerzoInicio());
                    nuevoDetalle.setHoraAlmuerzoFin(detalleOrigen.getHoraAlmuerzoFin());
                    nuevoDetalle.setTipoDia(detalleOrigen.getTipoDia());
                    nuevoDetalle.setTurno(detalleOrigen.getTurno());
                    nuevoDetalle.setOrigenTipoDia("manual");

                    detalleRepository.save(nuevoDetalle);
                }
            }

            fechaDestino = fechaDestino.plusDays(1);
            diaIndex++;
        }
    }

    // ==================== APLICAR SOLICITUDES ====================

    public void aplicarSolicitudesAprobadas(HorarioSemanal semana) {
        try {
            // Buscar solicitudes aprobadas que caigan en el rango de la semana
            List<Solicitud> solicitudes = solicitudRepository.findSolicitudesAprobadasEnRango(
                    semana.getFechaInicio(), semana.getFechaFin());

            if (solicitudes != null && !solicitudes.isEmpty()) {
                for (Solicitud solicitud : solicitudes) {
                    aplicarSolicitudASemana(solicitud, semana);
                }
            }
        } catch (Exception e) {
            // Log pero no falla - las solicitudes son opcionales
            logger.warn("Error aplicando solicitudes aprobadas: {}", e.getMessage());
        }
    }

    @Transactional
    public void aplicarSolicitudAprobada(Solicitud solicitud) {
        if (!"aprobado".equalsIgnoreCase(solicitud.getEstado())) {
            return;
        }

        // Buscar todas las semanas que se solapan con la solicitud
        List<HorarioSemanal> semanas = horarioSemanalRepository
                .findSemanasSolapadas(solicitud.getFechaInicio(), solicitud.getFechaFin());

        for (HorarioSemanal semana : semanas) {
            aplicarSolicitudASemana(solicitud, semana);
        }
    }

    private void aplicarSolicitudASemana(Solicitud solicitud, HorarioSemanal semana) {
        Integer empleadoId = solicitud.getEmpleado().getId();

        // Iterar por cada día de la solicitud dentro del rango de la semana
        LocalDate fecha = solicitud.getFechaInicio();
        while (!fecha.isAfter(solicitud.getFechaFin())) {
            // Solo si la fecha está dentro de la semana
            if (!fecha.isBefore(semana.getFechaInicio()) && !fecha.isAfter(semana.getFechaFin())) {
                Optional<HorarioSemanalDetalle> detalleOpt = detalleRepository
                        .findByEmpleadoIdAndFecha(empleadoId, fecha);

                if (detalleOpt.isPresent()) {
                    HorarioSemanalDetalle detalle = detalleOpt.get();
                    detalle.marcarPorSolicitud(solicitud);
                    detalleRepository.save(detalle);
                }
            }
            fecha = fecha.plusDays(1);
        }
    }

    @Transactional
    public void revertirSolicitud(Integer solicitudId) {
        List<HorarioSemanalDetalle> detalles = detalleRepository.findBySolicitudRefId(solicitudId);

        for (HorarioSemanalDetalle detalle : detalles) {
            // Restaurar desde horario base
            String diaSemana = detalle.getDiaSemana();
            Optional<Horario> horarioBase = horarioRepository
                    .findByEmpleadoIdAndDiaSemana(detalle.getEmpleado().getId(), diaSemana);

            detalle.revertirSolicitud();

            if (horarioBase.isPresent()) {
                detalle.copiarDesdeHorarioBase(horarioBase.get());
            }

            detalleRepository.save(detalle);
        }
    }

    // ==================== CONSULTAS ====================

    @Transactional(readOnly = true)
    public List<HorarioSemanalResponseDTO> obtenerTodas() {
        logger.info("Obteniendo todas las semanas de horarios");
        try {
            List<HorarioSemanal> semanas = horarioSemanalRepository.findAllByOrderByFechaInicioDesc();
            logger.info("Se encontraron {} semanas en la base de datos", semanas.size());

            if (semanas.isEmpty()) {
                return new ArrayList<>();
            }

            List<HorarioSemanalResponseDTO> result = new ArrayList<>();
            for (HorarioSemanal semana : semanas) {
                try {
                    result.add(construirResponseDTO(semana));
                } catch (Exception e) {
                    logger.error("Error al construir DTO para semana ID {}: {}", semana.getId(), e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error al obtener semanas: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public HorarioSemanalResponseDTO obtenerPorId(Integer id) {
        HorarioSemanal semana = horarioSemanalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Horario semanal no encontrado"));
        return construirResponseDTO(semana);
    }

    @Transactional(readOnly = true)
    public HorarioSemanalResponseDTO obtenerVigente() {
        LocalDate hoy = LocalDate.now();
        Optional<HorarioSemanal> semana = horarioSemanalRepository.findActivoByFechaContenida(hoy);

        if (semana.isPresent()) {
            return construirResponseDTO(semana.get());
        }

        // Si no hay activa, buscar cualquiera que contenga hoy
        semana = horarioSemanalRepository.findByFechaContenida(hoy);
        if (semana.isPresent()) {
            return construirResponseDTO(semana.get());
        }

        throw new RuntimeException("No hay horario semanal para la fecha actual");
    }

    @Transactional(readOnly = true)
    public HorarioSemanalResponseDTO obtenerPorFecha(LocalDate fecha) {
        Optional<HorarioSemanal> semana = horarioSemanalRepository.findByFechaContenida(fecha);
        if (semana.isPresent()) {
            return construirResponseDTO(semana.get());
        }
        throw new RuntimeException("No hay horario semanal para la fecha: " + fecha);
    }

    /**
     * Obtener el horario de un empleado desde la semana activa actual.
     * Retorna un HorarioSemanalDTO con las claves por día de semana (lunes, martes, etc.)
     * para ser compatible con el formato que usa el dashboard.
     */
    @Transactional(readOnly = true)
    public HorarioSemanalDTO obtenerMiHorarioVigente(Integer empleadoId) {
        LocalDate hoy = LocalDate.now();
        Optional<HorarioSemanal> semanaOpt = horarioSemanalRepository.findActivoByFechaContenida(hoy);

        if (!semanaOpt.isPresent()) {
            return null;
        }

        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        HorarioSemanalDTO dto = new HorarioSemanalDTO(
                empleado.getId(), empleado.getNombre(), empleado.getRol(), empleado.getCargo());

        List<HorarioSemanalDetalle> detalles = detalleRepository
                .findByHorarioSemanalIdAndEmpleadoId(semanaOpt.get().getId(), empleadoId);

        Map<String, HorarioSemanalDTO.HorarioDiaDTO> horariosSemana = new java.util.HashMap<>();
        for (String dia : java.util.Arrays.asList("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")) {
            horariosSemana.put(dia, null);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        for (HorarioSemanalDetalle det : detalles) {
            String dia = det.getDiaSemana() != null ? det.getDiaSemana().toLowerCase() : traducirDia(det.getFecha().getDayOfWeek().toString().toLowerCase());
            if (horariosSemana.containsKey(dia)) {
                horariosSemana.put(dia, new HorarioSemanalDTO.HorarioDiaDTO(
                        det.getId(),
                        det.getHoraEntrada() != null ? det.getHoraEntrada().format(fmt) : null,
                        det.getHoraSalida() != null ? det.getHoraSalida().format(fmt) : null,
                        det.getHoraAlmuerzoInicio() != null ? det.getHoraAlmuerzoInicio().format(fmt) : null,
                        det.getHoraAlmuerzoFin() != null ? det.getHoraAlmuerzoFin().format(fmt) : null,
                        det.getTipoDia(),
                        det.getTurno()
                ));
            }
        }

        dto.setHorariosSemana(horariosSemana);
        return dto;
    }

    // ==================== ACTUALIZAR ====================

    @Transactional
    public HorarioSemanalResponseDTO actualizarMultiplesDetalles(List<Integer> detalleIds, DetalleHorarioDiaDTO datos) {
        logger.info("Actualizando {} detalles con los mismos datos", detalleIds.size());

        HorarioSemanal semana = null;

        for (Integer detalleId : detalleIds) {
            HorarioSemanalDetalle detalle = detalleRepository.findById(detalleId)
                    .orElseThrow(() -> new RuntimeException("Detalle no encontrado con ID: " + detalleId));

            // Solo actualizar si no viene de una solicitud aprobada
            if ("solicitud_aprobada".equals(detalle.getOrigenTipoDia())) {
                logger.warn("Saltando detalle ID {} - viene de solicitud aprobada", detalleId);
                continue;
            }

            if (semana == null) {
                semana = detalle.getHorarioSemanal();
            }

            aplicarDatosADetalle(detalle, datos);
            detalleRepository.save(detalle);
        }

        if (semana == null) {
            throw new RuntimeException("No se pudo actualizar ningún día");
        }

        return construirResponseDTO(semana);
    }

    private void aplicarDatosADetalle(HorarioSemanalDetalle detalle, DetalleHorarioDiaDTO datos) {
        if (datos.getHoraEntrada() != null) {
            detalle.setHoraEntrada(parseTime(datos.getHoraEntrada()));
        }
        if (datos.getHoraSalida() != null) {
            detalle.setHoraSalida(parseTime(datos.getHoraSalida()));
        }
        if (datos.getHoraAlmuerzoInicio() != null) {
            detalle.setHoraAlmuerzoInicio(parseTime(datos.getHoraAlmuerzoInicio()));
        }
        if (datos.getHoraAlmuerzoFin() != null) {
            detalle.setHoraAlmuerzoFin(parseTime(datos.getHoraAlmuerzoFin()));
        }
        if (datos.getTipoDia() != null) {
            detalle.setTipoDia(datos.getTipoDia().toLowerCase());
        }
        if (datos.getTurno() != null) {
            detalle.setTurno(datos.getTurno().toLowerCase());
            // Si es turno tarde, limpiar almuerzo
            if ("tarde".equalsIgnoreCase(datos.getTurno())) {
                detalle.setHoraAlmuerzoInicio(null);
                detalle.setHoraAlmuerzoFin(null);
            }
        }
    }

    @Transactional
    public HorarioSemanalResponseDTO actualizarDetalle(Integer detalleId, DetalleHorarioDiaDTO request) {
        HorarioSemanalDetalle detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new RuntimeException("Detalle no encontrado"));

        // Solo actualizar si no viene de una solicitud aprobada
        if ("solicitud_aprobada".equals(detalle.getOrigenTipoDia())) {
            throw new RuntimeException("No se puede modificar un día asignado por solicitud aprobada. " +
                    "Primero debe rechazar la solicitud.");
        }

        if (request.getHoraEntrada() != null) {
            detalle.setHoraEntrada(parseTime(request.getHoraEntrada()));
        }
        if (request.getHoraSalida() != null) {
            detalle.setHoraSalida(parseTime(request.getHoraSalida()));
        }
        if (request.getHoraAlmuerzoInicio() != null) {
            detalle.setHoraAlmuerzoInicio(parseTime(request.getHoraAlmuerzoInicio()));
        }
        if (request.getHoraAlmuerzoFin() != null) {
            detalle.setHoraAlmuerzoFin(parseTime(request.getHoraAlmuerzoFin()));
        }
        if (request.getTipoDia() != null) {
            detalle.setTipoDia(request.getTipoDia().toLowerCase());
        }
        if (request.getTurno() != null) {
            detalle.setTurno(request.getTurno().toLowerCase());
            // Si es turno tarde, limpiar almuerzo
            if ("tarde".equalsIgnoreCase(request.getTurno())) {
                detalle.setHoraAlmuerzoInicio(null);
                detalle.setHoraAlmuerzoFin(null);
            }
        }

        detalleRepository.save(detalle);
        return construirResponseDTO(detalle.getHorarioSemanal());
    }

    @Transactional
    public HorarioSemanalResponseDTO cambiarEstado(Integer id, String nuevoEstado) {
        HorarioSemanal semana = horarioSemanalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Horario semanal no encontrado"));

        List<String> estadosValidos = Arrays.asList("borrador", "activo", "historico");
        if (!estadosValidos.contains(nuevoEstado.toLowerCase())) {
            throw new RuntimeException("Estado inválido: " + nuevoEstado +
                    ". Estados válidos: " + String.join(", ", estadosValidos));
        }

        semana.setEstado(nuevoEstado.toLowerCase());
        horarioSemanalRepository.save(semana);
        return construirResponseDTO(semana);
    }

    @Transactional
    public void eliminarSemana(Integer id) {
        HorarioSemanal semana = horarioSemanalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Horario semanal no encontrado"));

        if (!"borrador".equals(semana.getEstado())) {
            throw new RuntimeException("Solo se pueden eliminar semanas en estado 'borrador'");
        }

        horarioSemanalRepository.delete(semana);
    }

    // ==================== HELPERS ====================

    private HorarioSemanalResponseDTO construirResponseDTO(HorarioSemanal semana) {
        HorarioSemanalResponseDTO dto = new HorarioSemanalResponseDTO(semana);

        // Obtener todos los detalles ordenados
        List<HorarioSemanalDetalle> detalles = detalleRepository.findByHorarioSemanalIdOrdenado(semana.getId());

        // Agrupar por empleado
        Map<Integer, List<HorarioSemanalDetalle>> porEmpleado = detalles.stream()
                .collect(Collectors.groupingBy(d -> d.getEmpleado().getId()));

        for (Map.Entry<Integer, List<HorarioSemanalDetalle>> entry : porEmpleado.entrySet()) {
            List<HorarioSemanalDetalle> detallesEmpleado = entry.getValue();
            if (detallesEmpleado.isEmpty()) continue;

            Empleado empleado = detallesEmpleado.get(0).getEmpleado();

            // Excluir empleados inactivos
            if (!Boolean.TRUE.equals(empleado.getActivo())) continue;
            EmpleadoHorarioSemanalDTO empDTO = new EmpleadoHorarioSemanalDTO(
                    empleado.getId(),
                    empleado.getNombre(),
                    empleado.getRol(),
                    empleado.getCargo()
            );

            for (HorarioSemanalDetalle det : detallesEmpleado) {
                String fechaKey = det.getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE);
                empDTO.agregarDia(fechaKey, new DetalleHorarioDiaDTO(det));
            }

            dto.agregarEmpleado(empDTO);
        }

        // Ordenar empleados por nombre
        dto.getEmpleados().sort((a, b) -> a.getEmpleadoNombre().compareToIgnoreCase(b.getEmpleadoNombre()));

        dto.calcularEstadisticas();
        return dto;
    }

    private void validarFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null || fechaFin == null) {
            throw new RuntimeException("Las fechas de inicio y fin son requeridas");
        }
        if (fechaInicio.isAfter(fechaFin)) {
            throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }
        // Verificar que sea aproximadamente una semana (5-9 días)
        long dias = java.time.temporal.ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1;
        if (dias < 5 || dias > 9) {
            throw new RuntimeException("El rango debe ser de aproximadamente una semana (5-9 días)");
        }
    }

    private String traducirDia(String diaIngles) {
        switch (diaIngles) {
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

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        return LocalTime.parse(timeStr, TIME_FORMATTER);
    }
}
