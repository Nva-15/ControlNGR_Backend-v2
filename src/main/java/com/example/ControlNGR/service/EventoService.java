package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.*;
import com.example.ControlNGR.entity.*;
import com.example.ControlNGR.entity.Evento.EstadoEvento;
import com.example.ControlNGR.entity.Evento.TipoEvento;
import com.example.ControlNGR.entity.RespuestaEvento.ConfirmacionAsistencia;
import com.example.ControlNGR.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventoService {

    private static final Logger logger = LoggerFactory.getLogger(EventoService.class);

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private OpcionEventoRepository opcionEventoRepository;

    @Autowired
    private RespuestaEventoRepository respuestaEventoRepository;

    @Autowired
    private ComentarioEventoRepository comentarioEventoRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    // ==================== CRUD EVENTOS ====================

    @Transactional
    public EventoResponseDTO crearEvento(EventoRequestDTO request) {
        logger.info("Creando evento: {}", request.getTitulo());

        Optional<Empleado> creadorOpt = empleadoRepository.findById(request.getCreadoPorId());
        if (!creadorOpt.isPresent()) {
            throw new RuntimeException("Empleado creador no encontrado");
        }

        Empleado creador = creadorOpt.get();

        // Verificar permisos
        if (!Arrays.asList("admin", "supervisor").contains(creador.getRol().toLowerCase())) {
            throw new RuntimeException("Solo admin/supervisor pueden crear eventos");
        }

        Evento evento = new Evento();
        evento.setTitulo(request.getTitulo());
        evento.setDescripcion(request.getDescripcion());
        evento.setTipoEvento(TipoEvento.valueOf(request.getTipoEvento()));
        evento.setFechaInicio(request.getFechaInicio());
        evento.setFechaFin(request.getFechaFin());
        evento.setPermiteComentarios(request.getPermiteComentarios());
        evento.setRequiereRespuesta(request.getRequiereRespuesta());
        evento.setCreadoPor(creador);
        evento.setEstado(EstadoEvento.BORRADOR);

        // Configurar roles visibles
        if (request.getRolesVisibles() != null && !request.getRolesVisibles().isEmpty()) {
            evento.setRolesVisiblesList(request.getRolesVisibles());
        }

        Evento savedEvento = eventoRepository.save(evento);

        // Agregar opciones si es encuesta
        if (TipoEvento.ENCUESTA.equals(evento.getTipoEvento()) && request.getOpciones() != null) {
            int orden = 0;
            for (String textoOpcion : request.getOpciones()) {
                OpcionEvento opcion = new OpcionEvento(textoOpcion, orden++);
                savedEvento.addOpcion(opcion);
            }
            savedEvento = eventoRepository.save(savedEvento);
        }

        logger.info("Evento creado con ID: {}", savedEvento.getId());
        return new EventoResponseDTO(savedEvento);
    }

    @Transactional
    public EventoResponseDTO actualizarEvento(Integer id, EventoRequestDTO request, Integer empleadoId) {
        logger.info("Actualizando evento ID: {}", id);

        Evento evento = eventoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Evento no encontrado"));

        Empleado editor = empleadoRepository.findById(empleadoId)
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        // Verificar permisos
        if (!Arrays.asList("admin", "supervisor").contains(editor.getRol().toLowerCase())) {
            throw new RuntimeException("Sin permisos para editar eventos");
        }

        evento.setTitulo(request.getTitulo());
        evento.setDescripcion(request.getDescripcion());
        evento.setFechaInicio(request.getFechaInicio());
        evento.setFechaFin(request.getFechaFin());
        evento.setPermiteComentarios(request.getPermiteComentarios());
        evento.setRequiereRespuesta(request.getRequiereRespuesta());

        if (request.getRolesVisibles() != null) {
            evento.setRolesVisiblesList(request.getRolesVisibles());
        }

        // Actualizar opciones si es encuesta
        if (TipoEvento.ENCUESTA.equals(evento.getTipoEvento()) && request.getOpciones() != null) {
            evento.getOpciones().clear();
            int orden = 0;
            for (String textoOpcion : request.getOpciones()) {
                OpcionEvento opcion = new OpcionEvento(textoOpcion, orden++);
                evento.addOpcion(opcion);
            }
        }

        return new EventoResponseDTO(eventoRepository.save(evento));
    }

    @Transactional
    public EventoResponseDTO cambiarEstado(Integer id, String nuevoEstado, Integer empleadoId) {
        Evento evento = eventoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Evento no encontrado"));

        Empleado empleado = empleadoRepository.findById(empleadoId)
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (!Arrays.asList("admin", "supervisor").contains(empleado.getRol().toLowerCase())) {
            throw new RuntimeException("Sin permisos para cambiar estado");
        }

        evento.setEstado(EstadoEvento.valueOf(nuevoEstado));
        logger.info("Estado de evento {} cambiado a {}", id, nuevoEstado);
        return new EventoResponseDTO(eventoRepository.save(evento));
    }

    @Transactional
    public void eliminarEvento(Integer id, Integer empleadoId) {
        Evento evento = eventoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Evento no encontrado"));

        Empleado empleado = empleadoRepository.findById(empleadoId)
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (!Arrays.asList("admin", "supervisor").contains(empleado.getRol().toLowerCase())) {
            throw new RuntimeException("Sin permisos para eliminar eventos");
        }

        eventoRepository.delete(evento);
        logger.info("Evento eliminado: ID {}", id);
    }

    // ==================== CONSULTAS ====================

    @Transactional(readOnly = true)
    public List<EventoResponseDTO> obtenerEventosActivos(Integer empleadoId) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Evento> eventos = eventoRepository.findEventosActivos(ahora);

        // Obtener rol del empleado para filtrar
        Empleado empleado = empleadoRepository.findById(empleadoId)
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));
        String rolEmpleado = empleado.getRol().toLowerCase();

        return eventos.stream()
            .filter(e -> e.esVisibleParaRol(rolEmpleado))
            .map(e -> {
                EventoResponseDTO dto = new EventoResponseDTO(e);
                dto.setTotalRespuestas(eventoRepository.countRespuestasByEventoId(e.getId()));
                dto.setTotalComentarios(comentarioEventoRepository.countByEventoId(e.getId()));
                dto.setYaRespondio(respuestaEventoRepository.existsByEventoIdAndEmpleadoId(e.getId(), empleadoId));
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventoResponseDTO> obtenerProximosEventos(Integer empleadoId) {
        LocalDateTime ahora = LocalDateTime.now();

        Empleado empleado = empleadoRepository.findById(empleadoId)
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));
        String rolEmpleado = empleado.getRol().toLowerCase();

        return eventoRepository.findProximosEventos(ahora).stream()
            .filter(e -> e.esVisibleParaRol(rolEmpleado))
            .map(EventoResponseDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventoResponseDTO> obtenerTodosEventos() {
        return eventoRepository.findAll().stream()
            .sorted((a, b) -> b.getFechaCreacion().compareTo(a.getFechaCreacion()))
            .map(e -> {
                EventoResponseDTO dto = new EventoResponseDTO(e);
                dto.setTotalRespuestas(eventoRepository.countRespuestasByEventoId(e.getId()));
                dto.setTotalComentarios(comentarioEventoRepository.countByEventoId(e.getId()));
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventoResponseDTO obtenerEventoPorId(Integer id, Integer empleadoId) {
        Evento evento = eventoRepository.findByIdWithOpciones(id);
        if (evento == null) {
            throw new RuntimeException("Evento no encontrado");
        }

        EventoResponseDTO dto = new EventoResponseDTO(evento);
        dto.setTotalRespuestas(eventoRepository.countRespuestasByEventoId(id));
        dto.setTotalComentarios(comentarioEventoRepository.countByEventoId(id));
        dto.setYaRespondio(respuestaEventoRepository.existsByEventoIdAndEmpleadoId(id, empleadoId));

        return dto;
    }

    // ==================== RESPUESTAS ====================

    @Transactional
    public RespuestaEventoResponseDTO responderEvento(RespuestaEventoRequestDTO request) {
        logger.info("Registrando respuesta para evento ID: {}", request.getEventoId());

        Evento evento = eventoRepository.findById(request.getEventoId())
            .orElseThrow(() -> new RuntimeException("Evento no encontrado"));

        if (!EstadoEvento.ACTIVO.equals(evento.getEstado())) {
            throw new RuntimeException("El evento no esta activo");
        }

        Empleado empleado = empleadoRepository.findById(request.getEmpleadoId())
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (!Boolean.TRUE.equals(empleado.getActivo())) {
            throw new RuntimeException("El empleado no esta activo");
        }

        // Verificar si ya existe respuesta
        Optional<RespuestaEvento> respuestaExistente =
            respuestaEventoRepository.findByEventoIdAndEmpleadoId(request.getEventoId(), request.getEmpleadoId());

        RespuestaEvento respuesta;
        if (respuestaExistente.isPresent()) {
            respuesta = respuestaExistente.get();
            logger.info("Actualizando respuesta existente");
        } else {
            respuesta = new RespuestaEvento();
            respuesta.setEvento(evento);
            respuesta.setEmpleado(empleado);
        }

        // Establecer respuesta según tipo de evento
        switch (evento.getTipoEvento()) {
            case ENCUESTA:
                if (request.getOpcionId() != null) {
                    OpcionEvento opcion = opcionEventoRepository.findById(request.getOpcionId())
                        .orElseThrow(() -> new RuntimeException("Opcion no encontrada"));
                    respuesta.setOpcion(opcion);
                }
                break;
            case SI_NO:
                respuesta.setRespuestaSiNo(request.getRespuestaSiNo());
                break;
            case ASISTENCIA:
                if (request.getConfirmacionAsistencia() != null) {
                    respuesta.setConfirmacionAsistencia(
                        ConfirmacionAsistencia.valueOf(request.getConfirmacionAsistencia())
                    );
                }
                break;
            case INFORMATIVO:
                // Solo comentario
                break;
        }

        if (request.getComentario() != null && !request.getComentario().trim().isEmpty()) {
            respuesta.setComentario(request.getComentario());
        }

        return new RespuestaEventoResponseDTO(respuestaEventoRepository.save(respuesta));
    }

    @Transactional(readOnly = true)
    public List<RespuestaEventoResponseDTO> obtenerRespuestasEvento(Integer eventoId) {
        return respuestaEventoRepository.findByEventoIdWithEmpleado(eventoId).stream()
            .map(RespuestaEventoResponseDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RespuestaEventoResponseDTO> obtenerMiRespuesta(Integer eventoId, Integer empleadoId) {
        return respuestaEventoRepository.findByEventoIdAndEmpleadoId(eventoId, empleadoId)
            .map(RespuestaEventoResponseDTO::new);
    }

    // ==================== COMENTARIOS ====================

    @Transactional
    public ComentarioEventoDTO agregarComentario(Integer eventoId, Integer empleadoId, String texto) {
        Evento evento = eventoRepository.findById(eventoId)
            .orElseThrow(() -> new RuntimeException("Evento no encontrado"));

        if (!Boolean.TRUE.equals(evento.getPermiteComentarios())) {
            throw new RuntimeException("Este evento no permite comentarios");
        }

        Empleado empleado = empleadoRepository.findById(empleadoId)
            .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        ComentarioEvento comentario = new ComentarioEvento();
        comentario.setEvento(evento);
        comentario.setEmpleado(empleado);
        comentario.setComentario(texto);

        ComentarioEvento saved = comentarioEventoRepository.save(comentario);

        ComentarioEventoDTO dto = new ComentarioEventoDTO();
        dto.setId(saved.getId());
        dto.setEventoId(eventoId);
        dto.setEmpleadoId(empleadoId);
        dto.setEmpleadoNombre(empleado.getNombre());
        dto.setEmpleadoFoto(empleado.getFoto());
        dto.setComentario(texto);
        dto.setFechaComentario(saved.getFechaComentario());

        return dto;
    }

    @Transactional(readOnly = true)
    public List<ComentarioEventoDTO> obtenerComentarios(Integer eventoId) {
        return comentarioEventoRepository.findByEventoIdWithEmpleado(eventoId).stream()
            .map(c -> {
                ComentarioEventoDTO dto = new ComentarioEventoDTO();
                dto.setId(c.getId());
                dto.setEventoId(eventoId);
                dto.setEmpleadoId(c.getEmpleado().getId());
                dto.setEmpleadoNombre(c.getEmpleado().getNombre());
                dto.setEmpleadoFoto(c.getEmpleado().getFoto());
                dto.setComentario(c.getComentario());
                dto.setFechaComentario(c.getFechaComentario());
                return dto;
            })
            .collect(Collectors.toList());
    }

    // ==================== ESTADISTICAS ====================

    @Transactional(readOnly = true)
    public EstadisticasEventoDTO obtenerEstadisticas(Integer eventoId) {
        Evento evento = eventoRepository.findByIdWithOpciones(eventoId);
        if (evento == null) {
            throw new RuntimeException("Evento no encontrado");
        }

        EstadisticasEventoDTO stats = new EstadisticasEventoDTO();
        stats.setEventoId(eventoId);
        stats.setTipoEvento(evento.getTipoEvento().name());

        Long totalRespuestas = eventoRepository.countRespuestasByEventoId(eventoId);

        // Contar solo empleados con los roles visibles del evento
        List<String> rolesVisibles = evento.getRolesVisiblesList().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());
        Long totalEmpleados = empleadoRepository.countByRolInAndActivoTrue(rolesVisibles);

        stats.setTotalRespuestas(totalRespuestas);
        stats.setTotalEmpleados(totalEmpleados);
        stats.setPorcentajeParticipacion(
            totalEmpleados > 0 ? (totalRespuestas * 100.0 / totalEmpleados) : 0.0
        );

        switch (evento.getTipoEvento()) {
            case SI_NO:
                stats.setRespuestasSi(respuestaEventoRepository.countRespuestasSi(eventoId));
                stats.setRespuestasNo(respuestaEventoRepository.countRespuestasNo(eventoId));
                break;
            case ASISTENCIA:
                stats.setConfirmados(respuestaEventoRepository.countConfirmaciones(
                    eventoId, ConfirmacionAsistencia.CONFIRMADO));
                stats.setNoAsistiran(respuestaEventoRepository.countConfirmaciones(
                    eventoId, ConfirmacionAsistencia.NO_ASISTIRE));
                stats.setPendientes(respuestaEventoRepository.countConfirmaciones(
                    eventoId, ConfirmacionAsistencia.PENDIENTE));
                break;
            case ENCUESTA:
                List<Object[]> conteos = respuestaEventoRepository.countRespuestasPorOpcion(eventoId);
                Map<Integer, Long> conteoMap = new HashMap<>();
                for (Object[] row : conteos) {
                    conteoMap.put((Integer) row[0], (Long) row[1]);
                }

                List<EstadisticasEventoDTO.OpcionEstadisticaDTO> opcionesStats = new ArrayList<>();
                for (OpcionEvento opcion : evento.getOpciones()) {
                    EstadisticasEventoDTO.OpcionEstadisticaDTO opStat =
                        new EstadisticasEventoDTO.OpcionEstadisticaDTO();
                    opStat.setOpcionId(opcion.getId());
                    opStat.setTextoOpcion(opcion.getTextoOpcion());
                    Long votos = conteoMap.getOrDefault(opcion.getId(), 0L);
                    opStat.setVotos(votos);
                    opStat.setPorcentaje(totalRespuestas > 0 ? (votos * 100.0 / totalRespuestas) : 0.0);
                    opcionesStats.add(opStat);
                }
                stats.setOpcionesEstadisticas(opcionesStats);
                break;
            default:
                break;
        }

        return stats;
    }

    // ==================== TAREA PROGRAMADA ====================

    @Scheduled(fixedRate = 3600000) // Cada hora
    @Transactional
    public void finalizarEventosExpirados() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Evento> expirados = eventoRepository.findEventosExpirados(ahora);

        for (Evento evento : expirados) {
            evento.setEstado(EstadoEvento.FINALIZADO);
            eventoRepository.save(evento);
            logger.info("Evento finalizado automaticamente: ID {}", evento.getId());
        }
    }
}
