package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.*;
import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FuncionTurnoService funcionTurnoService;

    @Autowired
    private DiaFeriadoRepository diaFeriadoRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** Día creado sin horario base (la persona aún no lo tenía): se completa al registrarlo. */
    public static final String ORIGEN_SIN_HORARIO_BASE = "sin_horario_base";

    /** Tipos de día que forman parte del patrón semanal y se copian a la semana siguiente;
     *  los demás (vacaciones, descanso médico, licencia, permiso, compensado) son puntuales. */
    private static final Set<String> TIPOS_DIA_COPIABLES = Set.of("normal", "descanso");

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
        int agregados = 0;
        if (request.getCopiarDeId() != null) {
            logger.info("Copiando de semana origen ID: {}", request.getCopiarDeId());
            HorarioSemanal semanaOrigen = horarioSemanalRepository.findById(request.getCopiarDeId())
                    .orElseThrow(() -> new RuntimeException("Semana de origen no encontrada"));
            semana = horarioSemanalRepository.save(semana);
            logger.debug("Semana guardada con ID: {}", semana.getId());
            agregados = copiarDetallesDeSemana(semanaOrigen, semana);
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
        response.setColaboradoresAgregados(agregados);
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
            for (Empleado empleado : empleados) {
                HorarioSemanalDetalle detalle = new HorarioSemanalDetalle(semana, empleado, fecha);

                completarDesdeHorarioBase(detalle);
                detalleRepository.save(detalle);
            }

            fecha = fecha.plusDays(1);
        }
    }

    /**
     * Copia los turnos de la semana origen al personal activo con horario. Se empareja por día de la
     * semana (lunes con lunes). Quien no estaba en la semana origen (ingreso nuevo, reactivado o que
     * cambió a un rol con horario) entra con su horario base, igual que los días puntuales del origen
     * (vacaciones, permisos...), que no se repiten. Devuelve cuántos colaboradores se agregaron así.
     */
    private int copiarDetallesDeSemana(HorarioSemanal origen, HorarioSemanal destino) {
        Map<Integer, Map<DayOfWeek, HorarioSemanalDetalle>> origenPorEmpleado = new HashMap<>();
        for (HorarioSemanalDetalle d : detalleRepository.findByHorarioSemanalIdOrdenado(origen.getId())) {
            origenPorEmpleado.computeIfAbsent(d.getEmpleado().getId(), k -> new EnumMap<>(DayOfWeek.class))
                    .put(d.getFecha().getDayOfWeek(), d);
        }

        int agregados = 0;
        for (Empleado empleado : empleadoRepository.findEmpleadosConHorario()) {
            Map<DayOfWeek, HorarioSemanalDetalle> turnos = origenPorEmpleado.getOrDefault(empleado.getId(), Map.of());
            if (turnos.isEmpty()) {
                agregados++;
            }
            for (LocalDate fecha = destino.getFechaInicio(); !fecha.isAfter(destino.getFechaFin()); fecha = fecha.plusDays(1)) {
                HorarioSemanalDetalle nuevoDetalle = new HorarioSemanalDetalle(destino, empleado, fecha);
                HorarioSemanalDetalle detalleOrigen = turnos.get(fecha.getDayOfWeek());
                if (esCopiable(detalleOrigen)) {
                    nuevoDetalle.setHoraEntrada(detalleOrigen.getHoraEntrada());
                    nuevoDetalle.setHoraSalida(detalleOrigen.getHoraSalida());
                    nuevoDetalle.setHoraAlmuerzoInicio(detalleOrigen.getHoraAlmuerzoInicio());
                    nuevoDetalle.setHoraAlmuerzoFin(detalleOrigen.getHoraAlmuerzoFin());
                    nuevoDetalle.setTipoDia(detalleOrigen.getTipoDia());
                    nuevoDetalle.setTurno(detalleOrigen.getTurno());
                    nuevoDetalle.setFunciones(detalleOrigen.getFunciones());
                    nuevoDetalle.setOrigenTipoDia("manual");
                } else {
                    completarDesdeHorarioBase(nuevoDetalle);
                }
                detalleRepository.save(nuevoDetalle);
            }
        }
        if (agregados > 0) {
            logger.info("{}: {} colaborador(es) que no estaban en la semana origen entraron con su horario base",
                    destino.getNombre(), agregados);
        }
        return agregados;
    }

    /** Un día del origen se copia si es parte del patrón semanal: ni de una solicitud ni pendiente de horario base. */
    static boolean esCopiable(HorarioSemanalDetalle detalle) {
        return detalle != null
                && !"solicitud_aprobada".equals(detalle.getOrigenTipoDia())
                && !ORIGEN_SIN_HORARIO_BASE.equals(detalle.getOrigenTipoDia())
                && TIPOS_DIA_COPIABLES.contains(detalle.getTipoDia() == null ? "normal" : detalle.getTipoDia());
    }

    /** Llena el día con el horario base de la persona; si aún no lo tiene, queda pendiente de completar. */
    private void completarDesdeHorarioBase(HorarioSemanalDetalle detalle) {
        Optional<Horario> base = horarioRepository.findByEmpleadoIdAndDiaSemana(
                detalle.getEmpleado().getId(), detalle.getDiaSemana()).filter(h -> h.vigenteEn(detalle.getFecha()));
        if (base.isPresent()) {
            detalle.copiarDesdeHorarioBase(base.get());
        } else {
            detalle.setTipoDia("normal");
            detalle.setOrigenTipoDia(ORIGEN_SIN_HORARIO_BASE);
        }
    }

    // ==================== INCORPORAR PERSONAL A SEMANAS YA CREADAS ====================

    /**
     * Agrega al empleado (ingreso nuevo, reactivado o con un rol que ahora tiene horario) a la semana
     * vigente y a las futuras, desde hoy y con su horario base. Los días pasados no se tocan.
     * Devuelve los días agregados.
     */
    @Transactional
    public int incorporarEmpleado(Empleado empleado) {
        if (empleado == null || !Boolean.TRUE.equals(empleado.getActivo()) || !empleado.tieneHorario()) {
            return 0;
        }
        return incorporar(List.of(empleado), LocalDate.now());
    }

    /** Agrega a todo el personal activo con horario que falte en la semana vigente y las futuras. */
    @Transactional
    public int incorporarFaltantes() {
        return incorporar(empleadoRepository.findEmpleadosConHorario(), LocalDate.now());
    }

    int incorporar(List<Empleado> empleados, LocalDate hoy) {
        int total = 0;
        for (HorarioSemanal semana : horarioSemanalRepository.findNoTerminadasDesde(hoy)) {
            LocalDate desde = semana.getFechaInicio().isAfter(hoy) ? semana.getFechaInicio() : hoy;
            Set<String> existentes = new HashSet<>();
            for (Object[] fila : detalleRepository.findEmpleadoYFechaDesde(semana.getId(), desde)) {
                existentes.add(fila[0] + "|" + fila[1]);
            }
            Set<Integer> incorporados = new HashSet<>();
            for (Empleado empleado : empleados) {
                for (LocalDate fecha = desde; !fecha.isAfter(semana.getFechaFin()); fecha = fecha.plusDays(1)) {
                    if (existentes.contains(empleado.getId() + "|" + fecha)) continue;
                    HorarioSemanalDetalle detalle = new HorarioSemanalDetalle(semana, empleado, fecha);
                    completarDesdeHorarioBase(detalle);
                    detalleRepository.save(detalle);
                    incorporados.add(empleado.getId());
                    total++;
                }
            }
            if (!incorporados.isEmpty()) {
                // Solicitudes aprobadas que ya tuvieran en esas fechas (p. ej. al reactivar a alguien)
                for (Solicitud solicitud : solicitudRepository.findSolicitudesAprobadasEnRango(desde, semana.getFechaFin())) {
                    if (incorporados.contains(solicitud.getEmpleado().getId())) {
                        aplicarSolicitudASemana(solicitud, semana);
                    }
                }
                logger.info("{}: se agregaron {} colaborador(es) con su horario base desde {}",
                        semana.getNombre(), incorporados.size(), desde);
            }
        }
        return total;
    }

    /**
     * Cuando se registra o cambia el horario base de alguien, completa sus días de hoy en adelante
     * que se crearon sin horario base. Los días que el jefe ya editó no se tocan.
     */
    @Transactional
    public void completarDiasSinHorarioBase(Integer empleadoId) {
        List<HorarioSemanalDetalle> pendientes = detalleRepository
                .findByEmpleadoIdAndFechaGreaterThanEqualAndOrigenTipoDia(empleadoId, LocalDate.now(), ORIGEN_SIN_HORARIO_BASE);
        for (HorarioSemanalDetalle detalle : pendientes) {
            completarDesdeHorarioBase(detalle);
            detalleRepository.save(detalle);
        }
    }

    /** Al iniciar y cada día a las 00:20 (hora de Lima): nadie con horario queda fuera de las semanas creadas. */
    @EventListener(ApplicationReadyEvent.class)
    public void alIniciar() {
        sincronizarPersonal();
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "America/Lima")
    public void programado() {
        sincronizarPersonal();
    }

    private void sincronizarPersonal() {
        try {
            // Llamada interna: se abre la transaccion explicitamente (el proxy de @Transactional no aplica)
            new TransactionTemplate(transactionManager).execute(status -> incorporarFaltantes());
        } catch (Exception e) {
            logger.error("Error al agregar personal a las semanas de horario: {}", e.getMessage(), e);
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
        Set<Integer> empleados = new HashSet<>();

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
            validarDescansoFeriado(detalle);
            detalle.setOrigenTipoDia("manual");
            detalleRepository.save(detalle);
            empleados.add(detalle.getEmpleado().getId());
        }

        if (semana == null) {
            throw new RuntimeException("No se pudo actualizar ningún día");
        }

        validarDescansos(semana, empleados);
        return construirResponseDTO(semana);
    }

    /**
     * Arma la semana de una persona en un paso: cada día con su turno (o de descanso). Los días asignados por
     * una solicitud aprobada no se tocan. La semana no puede quedar con más de un día de descanso.
     */
    @Transactional
    public HorarioSemanalResponseDTO actualizarSemanaEmpleado(Integer semanaId, Integer empleadoId, TurnoSemanaDTO turnos) {
        turnos.validar();
        HorarioSemanal semana = horarioSemanalRepository.findById(semanaId)
                .orElseThrow(() -> new RuntimeException("Horario semanal no encontrado"));
        Map<String, HorarioSemanalDetalle> porFecha = new HashMap<>();
        for (HorarioSemanalDetalle d : detalleRepository.findByHorarioSemanalIdAndEmpleadoId(semanaId, empleadoId)) {
            porFecha.put(d.getFecha().toString(), d);
        }
        int actualizados = 0;
        for (TurnoSemanaDTO.Dia dia : turnos.dias()) {
            HorarioSemanalDetalle detalle = porFecha.get(dia.clave());
            if (detalle == null) {
                throw new IllegalArgumentException("El día " + dia.clave() + " no pertenece a la semana de este colaborador");
            }
            if ("solicitud_aprobada".equals(detalle.getOrigenTipoDia())) {
                continue;
            }
            aplicarTurno(detalle, dia);
            validarDescansoFeriado(detalle);
            detalleRepository.save(detalle);
            actualizados++;
        }
        if (actualizados == 0) {
            throw new IllegalArgumentException("No hay días que se puedan modificar (los días por solicitud aprobada no se cambian)");
        }
        validarDescansos(porFecha.values());
        return construirResponseDTO(semana);
    }

    private void aplicarTurno(HorarioSemanalDetalle detalle, TurnoSemanaDTO.Dia dia) {
        detalle.setTipoDia(dia.tipoDia());
        detalle.setOrigenTipoDia("manual");
        if (dia.esLaboral()) {
            detalle.setTurno(dia.turnoNormalizado());
            detalle.setHoraEntrada(dia.horaEntrada());
            detalle.setHoraSalida(dia.horaSalida());
            detalle.setHoraAlmuerzoInicio(dia.almuerzoInicio());
            detalle.setHoraAlmuerzoFin(dia.almuerzoFin());
            detalle.setFunciones(funcionTurnoService.normalizar(dia.funciones(), detalle.getFunciones()));
        } else {
            detalle.setTurno(null);
            detalle.setHoraEntrada(null);
            detalle.setHoraSalida(null);
            detalle.setHoraAlmuerzoInicio(null);
            detalle.setHoraAlmuerzoFin(null);
            detalle.setFunciones(null);
        }
    }

    /** "Descanso feriado" solo en una fecha registrada como feriado (Panel admin → Feriados). */
    private void validarDescansoFeriado(HorarioSemanalDetalle detalle) {
        if (TurnoSemanaDTO.DESCANSO_FERIADO.equals(detalle.getTipoDia())
                && diaFeriadoRepository.findByFechaAndActivoTrue(detalle.getFecha()).isEmpty()) {
            throw new IllegalArgumentException("El " + detalle.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " no es feriado: \"Descanso feriado\" solo se usa en los feriados registrados (Panel admin → Feriados)."
                    + " Si descansa a cambio de un feriado trabajado, registre una solicitud de compensación.");
        }
    }

    /** Feriados activos dentro de la semana: fecha → descripción. */
    private Map<String, String> feriadosDe(HorarioSemanal semana) {
        Map<String, String> feriados = new LinkedHashMap<>();
        for (DiaFeriado f : diaFeriadoRepository.findByFechaBetweenAndActivoTrueOrderByFechaAsc(semana.getFechaInicio(), semana.getFechaFin())) {
            feriados.put(f.getFecha().toString(), f.getDescripcion());
        }
        return feriados;
    }

    /** Una persona no puede tener más de un día de descanso en la semana (vacaciones, permisos... no cuentan). */
    static void validarDescansos(Collection<HorarioSemanalDetalle> diasDeLaPersona) {
        long descansos = diasDeLaPersona.stream().filter(d -> TurnoSemanaDTO.DESCANSO.equals(d.getTipoDia())).count();
        if (descansos > TurnoSemanaDTO.MAX_DESCANSOS) {
            String nombre = diasDeLaPersona.iterator().next().getEmpleado().getNombre();
            throw new IllegalArgumentException(TurnoSemanaDTO.mensajeDescansos(nombre, descansos));
        }
    }

    /** Tras editar días sueltos: revisa los descansos de cada persona afectada en su semana. */
    private void validarDescansos(HorarioSemanal semana, Collection<Integer> empleadoIds) {
        for (Integer empleadoId : new HashSet<>(empleadoIds)) {
            validarDescansos(detalleRepository.findByHorarioSemanalIdAndEmpleadoId(semana.getId(), empleadoId));
        }
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
        aplicarFunciones(detalle, datos.getFunciones());
    }

    /** Las funciones solo aplican a días laborales; si no se envían, se conservan. */
    private void aplicarFunciones(HorarioSemanalDetalle detalle, List<String> funciones) {
        if (!"normal".equals(detalle.getTipoDia())) {
            detalle.setFunciones(null);
        } else if (funciones != null) {
            detalle.setFunciones(funcionTurnoService.normalizar(funciones, detalle.getFunciones()));
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
        aplicarFunciones(detalle, request.getFunciones());
        validarDescansoFeriado(detalle);

        detalle.setOrigenTipoDia("manual");
        detalleRepository.save(detalle);
        validarDescansos(detalle.getHorarioSemanal(), List.of(detalle.getEmpleado().getId()));
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

            // Excluir empleados inactivos y roles sin horario (configurable en el panel admin)
            if (!Boolean.TRUE.equals(empleado.getActivo()) || !empleado.tieneHorario()) continue;
            EmpleadoHorarioSemanalDTO empDTO = new EmpleadoHorarioSemanalDTO(
                    empleado.getId(),
                    empleado.getNombre(),
                    empleado.getRol(),
                    empleado.getCargo()
            );
            empDTO.setEmpleadoDepartamento(empleado.getDepartamentoNombre());

            for (HorarioSemanalDetalle det : detallesEmpleado) {
                String fechaKey = det.getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE);
                empDTO.agregarDia(fechaKey, new DetalleHorarioDiaDTO(det));
            }

            dto.agregarEmpleado(empDTO);
        }

        // Ordenar empleados por nombre
        dto.getEmpleados().sort((a, b) -> a.getEmpleadoNombre().compareToIgnoreCase(b.getEmpleadoNombre()));

        dto.calcularEstadisticas();
        dto.setFeriados(feriadosDe(semana));
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
