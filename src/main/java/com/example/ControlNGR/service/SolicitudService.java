package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.SolicitudRequestDTO;
import com.example.ControlNGR.dto.SolicitudResponseDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Solicitud;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.SolicitudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SolicitudService {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudService.class);

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private HorarioSemanalService horarioSemanalService;

    @Autowired
    private EmailService emailService;

    private static final DateTimeFormatter AUDIT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Verifica si el empleado puede editar la solicitud. */
    public boolean puedeEditarSolicitud(Integer solicitudId, Integer empleadoId, String rolEmpleado) {
        Optional<Solicitud> solicitudOpt = solicitudRepository.findById(solicitudId);
        if (!solicitudOpt.isPresent()) {
            return false;
        }

        Solicitud solicitud = solicitudOpt.get();
        boolean esMiSolicitud = solicitud.getEmpleado().getId().equals(empleadoId);

        return esMiSolicitud && "pendiente".equals(solicitud.getEstado());
    }

    /** Verifica si el empleado puede gestionar la solicitud. */
    public boolean puedeGestionarSolicitud(Integer solicitudId, Integer empleadoId, String rolEmpleado, String accion) {
        Optional<Solicitud> solicitudOpt = solicitudRepository.findById(solicitudId);
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(empleadoId);

        if (!solicitudOpt.isPresent() || !empleadoOpt.isPresent()) {
            return false;
        }

        Solicitud solicitud = solicitudOpt.get();
        boolean esMiSolicitud = solicitud.getEmpleado().getId().equals(empleadoId);
        String rolSolicitud = solicitud.getEmpleado().getRol();

        if (esMiSolicitud) {
            return false;
        }

        if (!Arrays.asList("pendiente", "aprobado", "rechazado").contains(solicitud.getEstado())) {
            return false;
        }

        switch (rolSolicitud) {
            case "tecnico":
            case "hd":
            case "noc":
                return "supervisor".equals(rolEmpleado) || "admin".equals(rolEmpleado);
            case "supervisor":
                return "admin".equals(rolEmpleado);
            case "admin":
                return "admin".equals(rolEmpleado);
            default:
                return false;
        }
    }

    /** Gestiona una solicitud (aprobar/rechazar). */
    public SolicitudResponseDTO gestionarSolicitud(Integer id, String estado, Integer idAprobador, String comentarios) {
        Optional<Solicitud> solicitudOpt = solicitudRepository.findById(id);
        Optional<Empleado> aprobadorOpt = empleadoRepository.findById(idAprobador);

        if (!solicitudOpt.isPresent()) {
            throw new RuntimeException("Solicitud no encontrada");
        }

        if (!aprobadorOpt.isPresent()) {
            throw new RuntimeException("Aprobador no encontrado");
        }

        Solicitud solicitud = solicitudOpt.get();
        Empleado aprobador = aprobadorOpt.get();

        if (!puedeGestionarSolicitud(id, idAprobador, aprobador.getRol(), estado)) {
            throw new RuntimeException("No tiene permisos para gestionar esta solicitud");
        }

        if (solicitud.getEmpleado().getId().equals(idAprobador)) {
            throw new RuntimeException("No puede aprobar/rechazar su propia solicitud");
        }

        if (!Arrays.asList("aprobado", "rechazado").contains(estado.toLowerCase())) {
            throw new RuntimeException("Estado inválido para gestión");
        }

        String estadoAnterior = solicitud.getEstado();
        boolean esCorreccion = Arrays.asList("aprobado", "rechazado").contains(estadoAnterior);

        solicitud.setEstado(estado.toLowerCase());
        solicitud.setAprobadoPor(aprobador);
        solicitud.setFechaAprobacion(LocalDateTime.now());

        // Agregar nota de corrección si aplica
        if (esCorreccion && !estadoAnterior.equals(estado.toLowerCase())) {
            String motivoActual = solicitud.getMotivo() != null ? solicitud.getMotivo() : "";

            if (motivoActual.contains("[Estado corregido")) {
                motivoActual = motivoActual.split("\\[Estado corregido")[0].trim();
            }

            String notaCorreccion = "\n\n[Estado corregido por " + aprobador.getNombre() +
                                   " - " + LocalDateTime.now().format(AUDIT_FORMATTER) +
                                   "] De " + estadoAnterior.toUpperCase() + " a " + estado.toUpperCase();
            if (comentarios != null && !comentarios.trim().isEmpty()) {
                notaCorreccion += "\nMotivo: " + comentarios;
            }
            solicitud.setMotivo(motivoActual + notaCorreccion);
        } else if (comentarios != null && !comentarios.trim().isEmpty()) {
            String motivoActual = solicitud.getMotivo() != null ? solicitud.getMotivo() : "";
            solicitud.setMotivo(motivoActual + "\n\nComentarios de gestión: " + comentarios);
        }

        Solicitud savedSolicitud = solicitudRepository.save(solicitud);

        // Integrar con horarios semanales
        try {
            if ("aprobado".equals(estado.toLowerCase())) {
                horarioSemanalService.aplicarSolicitudAprobada(savedSolicitud);
            } else if (esCorreccion && "rechazado".equals(estado.toLowerCase()) &&
                       "aprobado".equals(estadoAnterior)) {
                horarioSemanalService.revertirSolicitud(savedSolicitud.getId());
            }
        } catch (Exception e) {
            logger.error("Error al actualizar horarios semanales: {}", e.getMessage());
        }

        // Enviar notificación
        notificarGestionSolicitud(savedSolicitud, aprobador, comentarios);

        return new SolicitudResponseDTO(savedSolicitud);
    }

    private void notificarGestionSolicitud(Solicitud solicitud, Empleado aprobador, String comentarios) {
        try {
            Empleado empleado = solicitud.getEmpleado();
            if (empleado.getEmail() != null && !empleado.getEmail().isEmpty()) {
                String fechaInicio = solicitud.getFechaInicio().format(DATE_FORMATTER);
                String fechaFin = solicitud.getFechaFin().format(DATE_FORMATTER);
                String nombreAprobador = aprobador != null ? aprobador.getNombre() : "Sistema";

                emailService.enviarNotificacionSolicitud(
                    empleado.getEmail(),
                    empleado.getNombre(),
                    solicitud.getTipo(),
                    solicitud.getEstado(),
                    comentarios,
                    fechaInicio,
                    fechaFin,
                    nombreAprobador
                );
            }
        } catch (Exception e) {
            logger.error("Error enviando notificación de gestión: {}", e.getMessage());
        }
    }

    /** Edita una solicitud existente. */
    public SolicitudResponseDTO editarSolicitud(Integer id, Map<String, Object> payload,
                                                Integer empleadoEditorId, String rolEditor) {

        Optional<Solicitud> solicitudOpt = solicitudRepository.findById(id);
        if (!solicitudOpt.isPresent()) {
            throw new RuntimeException("Solicitud no encontrada");
        }

        Solicitud solicitud = solicitudOpt.get();

        if (!puedeEditarSolicitud(id, empleadoEditorId, rolEditor)) {
            throw new RuntimeException("No tiene permisos para editar esta solicitud");
        }

        Optional<Empleado> editorOpt = empleadoRepository.findById(empleadoEditorId);
        if (!editorOpt.isPresent()) {
            throw new RuntimeException("Empleado editor no encontrado");
        }
        String nombreEditor = editorOpt.get().getNombre();

        StringBuilder nuevoMotivo = new StringBuilder();

        if (payload.containsKey("fechaInicio")) {
            String fechaInicioStr = (String) payload.get("fechaInicio");
            LocalDate fechaInicio = LocalDate.parse(fechaInicioStr);
            if (solicitud.getFechaFin() != null && fechaInicio.isAfter(solicitud.getFechaFin())) {
                throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
            }
            solicitud.setFechaInicio(fechaInicio);
        }

        if (payload.containsKey("fechaFin")) {
            String fechaFinStr = (String) payload.get("fechaFin");
            LocalDate fechaFin = LocalDate.parse(fechaFinStr);
            if (solicitud.getFechaInicio() != null && fechaFin.isBefore(solicitud.getFechaInicio())) {
                throw new RuntimeException("La fecha de fin no puede ser anterior a la fecha de inicio");
            }
            solicitud.setFechaFin(fechaFin);
        }

        if (payload.containsKey("tipo")) {
            solicitud.setTipo((String) payload.get("tipo"));
        }

        if (payload.containsKey("motivo")) {
            String motivoNuevo = (String) payload.get("motivo");
            String motivoBase = motivoNuevo;

            if (motivoBase != null && motivoBase.contains("CONFLICTO DE FECHAS:")) {
                motivoBase = motivoBase.split("CONFLICTO DE FECHAS:")[0].trim();
            }

            if (motivoBase != null && motivoBase.contains("[Editado por")) {
                motivoBase = motivoBase.split("\\[Editado por")[0].trim();
            }

            nuevoMotivo.append(motivoBase);
            nuevoMotivo.append("\n\n[Editado por ").append(nombreEditor)
                      .append(" - ").append(LocalDateTime.now().format(AUDIT_FORMATTER)).append("]");

            solicitud.setMotivo(nuevoMotivo.toString());
        }

        Solicitud solicitudActualizada = solicitudRepository.save(solicitud);
        return new SolicitudResponseDTO(solicitudActualizada);
    }

    /** Crea una nueva solicitud. */
    public SolicitudResponseDTO crearSolicitud(SolicitudRequestDTO request) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findById(request.getEmpleadoId());
        if (!empleadoOpt.isPresent()) {
            throw new RuntimeException("Empleado no encontrado");
        }

        Empleado empleado = empleadoOpt.get();

        if (request.getFechaInicio() == null || request.getFechaFin() == null) {
            throw new RuntimeException("Fechas requeridas");
        }

        if (request.getFechaInicio().isAfter(request.getFechaFin())) {
            throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }

        // Verificar conflictos por rol
        List<Solicitud> conflictosRol = verificarConflictosPorRolYFechas(
            request.getEmpleadoId(),
            empleado.getRol(),
            request.getFechaInicio(),
            request.getFechaFin()
        );

        String motivo = request.getMotivo();

        if (motivo != null && motivo.contains("CONFLICTO DE FECHAS:")) {
            motivo = motivo.split("CONFLICTO DE FECHAS:")[0].trim();
        }

        // Agregar nota de conflicto si existe
        if (!conflictosRol.isEmpty()) {
            StringBuilder conflictoInfo = new StringBuilder();
            conflictoInfo.append("\n\nCONFLICTO DE FECHAS: ");
            conflictoInfo.append("Existe(n) ").append(conflictosRol.size()).append(" solicitud(es) ");
            conflictoInfo.append("de compañeros del mismo rol en este período:\n");

            for (Solicitud conf : conflictosRol) {
                conflictoInfo.append("- ").append(conf.getEmpleado().getNombre());
                conflictoInfo.append(" (").append(conf.getTipo()).append(": ");
                conflictoInfo.append(conf.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                conflictoInfo.append(" - ");
                conflictoInfo.append(conf.getFechaFin().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                conflictoInfo.append(")\n");
            }

            motivo = motivo + conflictoInfo.toString();
        }

        Solicitud solicitud = new Solicitud();
        solicitud.setEmpleado(empleado);
        solicitud.setTipo(request.getTipo());
        solicitud.setFechaInicio(request.getFechaInicio());
        solicitud.setFechaFin(request.getFechaFin());
        solicitud.setMotivo(motivo);
        solicitud.setEstado("pendiente");

        Solicitud savedSolicitud = solicitudRepository.save(solicitud);

        // Enviar notificaciones
        notificarNuevaSolicitud(empleado, savedSolicitud);

        return new SolicitudResponseDTO(savedSolicitud);
    }

    private void notificarNuevaSolicitud(Empleado empleado, Solicitud solicitud) {
        try {
            String rolEmpleado = empleado.getRol();
            String fechaInicio = solicitud.getFechaInicio().format(DATE_FORMATTER);
            String fechaFin = solicitud.getFechaFin().format(DATE_FORMATTER);

            if ("supervisor".equals(rolEmpleado)) {
                // Notificar a admins
                List<Empleado> admins = empleadoRepository.findByRol("admin");
                for (Empleado admin : admins) {
                    if (admin.getEmail() != null && !admin.getEmail().isEmpty()) {
                        emailService.enviarNotificacionSolicitudSupervisor(
                            admin.getEmail(),
                            admin.getNombre(),
                            empleado.getNombre(),
                            solicitud.getTipo(),
                            fechaInicio,
                            fechaFin
                        );
                    }
                }
            } else if ("tecnico".equals(rolEmpleado) || "hd".equals(rolEmpleado) || "noc".equals(rolEmpleado)) {
                // Notificar a supervisores
                List<Empleado> supervisores = empleadoRepository.findByRol("supervisor");
                for (Empleado supervisor : supervisores) {
                    if (supervisor.getEmail() != null && !supervisor.getEmail().isEmpty()) {
                        emailService.enviarNotificacionNuevaSolicitud(
                            supervisor.getEmail(),
                            supervisor.getNombre(),
                            empleado.getNombre(),
                            solicitud.getTipo(),
                            fechaInicio,
                            fechaFin
                        );
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error enviando notificaciones de nueva solicitud: {}", e.getMessage());
        }
    }

    /** Verifica conflictos de fecha para un empleado. */
    public List<Solicitud> verificarConflictosFecha(Integer empleadoId, LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null || fechaFin == null) {
            throw new RuntimeException("Fechas inválidas");
        }

        if (fechaInicio.isAfter(fechaFin)) {
            throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }

        return solicitudRepository.findConflictosPorRangoFechas(empleadoId, fechaInicio, fechaFin);
    }

    /** Verifica conflictos de fecha por rol. */
    public List<Solicitud> verificarConflictosPorRolYFechas(Integer empleadoId, String rolEmpleado, LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null || fechaFin == null) {
            throw new RuntimeException("Fechas inválidas");
        }

        if (fechaInicio.isAfter(fechaFin)) {
            throw new RuntimeException("La fecha de inicio no puede ser posterior a la fecha de fin");
        }

        return solicitudRepository.findConflictosPorRolYRangoFechas(empleadoId, rolEmpleado, fechaInicio, fechaFin);
    }

    /** Obtiene las solicitudes del empleado. */
    public List<SolicitudResponseDTO> obtenerMisSolicitudes(Integer empleadoId) {
        return solicitudRepository.findByEmpleadoIdOrderByFechaSolicitudDesc(empleadoId)
                .stream()
                .map(SolicitudResponseDTO::new)
                .collect(Collectors.toList());
    }

    /** Obtiene las solicitudes pendientes. */
    public List<SolicitudResponseDTO> obtenerPendientes() {
        return solicitudRepository.findSolicitudesPendientes()
                .stream()
                .map(SolicitudResponseDTO::new)
                .collect(Collectors.toList());
    }

    /** Obtiene todas las solicitudes. */
    public List<SolicitudResponseDTO> obtenerTodas() {
        return solicitudRepository.findAll()
                .stream()
                .map(SolicitudResponseDTO::new)
                .collect(Collectors.toList());
    }

    /** Edita una solicitud con datos del editor en el payload. */
    public SolicitudResponseDTO editarSolicitud(Integer id, Map<String, Object> payload) {
        if (!payload.containsKey("empleadoEditorId") || !payload.containsKey("rolEditor")) {
            throw new RuntimeException("Datos de editor requeridos");
        }
        return editarSolicitud(id, payload, (Integer) payload.get("empleadoEditorId"), (String) payload.get("rolEditor"));
    }

    /** Exporta solicitudes según el tipo de reporte. */
    public Map<String, Object> exportarSolicitudes(String tipoReporte, Integer empleadoId) {
        Map<String, Object> reporte = new HashMap<>();

        if ("mis-solicitudes".equals(tipoReporte) && empleadoId != null) {
            List<SolicitudResponseDTO> solicitudes = obtenerMisSolicitudes(empleadoId);
            reporte.put("titulo", "Mis Solicitudes");
            reporte.put("total", solicitudes.size());
            reporte.put("solicitudes", solicitudes);
        } else if ("pendientes".equals(tipoReporte)) {
            List<SolicitudResponseDTO> solicitudes = obtenerPendientes();
            reporte.put("titulo", "Solicitudes Pendientes");
            reporte.put("total", solicitudes.size());
            reporte.put("solicitudes", solicitudes);
        } else if ("historial".equals(tipoReporte)) {
            List<SolicitudResponseDTO> todas = obtenerTodas();
            List<SolicitudResponseDTO> historial = todas.stream()
                    .filter(s -> !"pendiente".equals(s.getEstado()))
                    .collect(Collectors.toList());
            reporte.put("titulo", "Historial de Solicitudes");
            reporte.put("total", historial.size());
            reporte.put("solicitudes", historial);
        } else {
            List<SolicitudResponseDTO> todas = obtenerTodas();
            reporte.put("titulo", "Todas las Solicitudes");
            reporte.put("total", todas.size());
            reporte.put("solicitudes", todas);
        }

        reporte.put("fecha_generacion", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        return reporte;
    }

    /** Elimina una solicitud pendiente. */
    public void eliminarSolicitud(Integer id, Integer empleadoId) {
        Optional<Solicitud> solicitudOpt = solicitudRepository.findById(id);

        if (!solicitudOpt.isPresent()) {
            throw new RuntimeException("Solicitud no encontrada");
        }

        Solicitud solicitud = solicitudOpt.get();

        // Verificar que el empleado sea el dueño de la solicitud
        if (!solicitud.getEmpleado().getId().equals(empleadoId)) {
            throw new RuntimeException("No tiene permisos para eliminar esta solicitud");
        }

        // Solo se pueden eliminar solicitudes pendientes
        if (!"pendiente".equals(solicitud.getEstado())) {
            throw new RuntimeException("Solo se pueden eliminar solicitudes pendientes");
        }

        solicitudRepository.deleteById(id);
        logger.info("Solicitud {} eliminada por empleado {}", id, empleadoId);
    }
}
