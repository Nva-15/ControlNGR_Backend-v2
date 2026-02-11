package com.example.ControlNGR.service;

import com.example.ControlNGR.dto.NotificacionResumenDTO;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.Evento;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.EventoRepository;
import com.example.ControlNGR.repository.RespuestaEventoRepository;
import com.example.ControlNGR.repository.SolicitudRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionService {

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private RespuestaEventoRepository respuestaEventoRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Transactional(readOnly = true)
    public NotificacionResumenDTO obtenerResumen(Empleado empleado) {
        NotificacionResumenDTO resumen = new NotificacionResumenDTO();
        LocalDateTime hace7Dias = LocalDateTime.now().minusDays(7);

        // Solicitudes aprobadas/rechazadas recientemente (del propio empleado)
        resumen.setSolicitudesAprobadas(
                solicitudRepository.countSolicitudesConEstadoDesde(empleado.getId(), "aprobado", hace7Dias));
        resumen.setSolicitudesRechazadas(
                solicitudRepository.countSolicitudesConEstadoDesde(empleado.getId(), "rechazado", hace7Dias));

        // Solicitudes pendientes de aprobacion (solo para admin/supervisor)
        String rol = empleado.getRol().toLowerCase();
        if ("admin".equals(rol) || "supervisor".equals(rol)) {
            resumen.setSolicitudesPendientes(
                    solicitudRepository.findSolicitudesPendientes().size());
        }

        // Eventos activos sin responder
        String rolEmpleado = empleado.getRol().toLowerCase();
        LocalDateTime ahora = LocalDateTime.now();
        List<Evento> eventosActivos = eventoRepository.findEventosActivos(ahora);

        long sinResponder = eventosActivos.stream()
                .filter(e -> e.esVisibleParaRol(rolEmpleado))
                .filter(e -> !respuestaEventoRepository.existsByEventoIdAndEmpleadoId(e.getId(), empleado.getId()))
                .count();
        resumen.setEventosSinResponder(sinResponder);

        resumen.calcularTotal();
        return resumen;
    }
}
