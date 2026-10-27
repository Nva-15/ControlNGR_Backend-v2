package com.example.ControlNGR.dto;

public class NotificacionResumenDTO {

    private long solicitudesPendientes;    // pendientes de aprobacion (admin/supervisor)
    private long solicitudesAprobadas;     // mis solicitudes aprobadas recientemente
    private long solicitudesRechazadas;    // mis solicitudes rechazadas recientemente
    private long eventosSinResponder;      // eventos activos sin responder
    private long alertasAsistencia;        // alertas de asistencia nuevas (jefaturas)
    private boolean miRecordHabilitado;    // muestra "Mi asistencia" en el menu (panel habilitado y marca asistencia)
    private long totalNotificaciones;      // suma total

    public NotificacionResumenDTO() {}

    public void calcularTotal() {
        this.totalNotificaciones = solicitudesPendientes + solicitudesAprobadas
                + solicitudesRechazadas + eventosSinResponder + alertasAsistencia;
    }

    public boolean isMiRecordHabilitado() { return miRecordHabilitado; }
    public void setMiRecordHabilitado(boolean v) { this.miRecordHabilitado = v; }

    public long getAlertasAsistencia() { return alertasAsistencia; }
    public void setAlertasAsistencia(long v) { this.alertasAsistencia = v; }

    public long getSolicitudesPendientes() { return solicitudesPendientes; }
    public void setSolicitudesPendientes(long v) { this.solicitudesPendientes = v; }

    public long getSolicitudesAprobadas() { return solicitudesAprobadas; }
    public void setSolicitudesAprobadas(long v) { this.solicitudesAprobadas = v; }

    public long getSolicitudesRechazadas() { return solicitudesRechazadas; }
    public void setSolicitudesRechazadas(long v) { this.solicitudesRechazadas = v; }

    public long getEventosSinResponder() { return eventosSinResponder; }
    public void setEventosSinResponder(long v) { this.eventosSinResponder = v; }

    public long getTotalNotificaciones() { return totalNotificaciones; }
    public void setTotalNotificaciones(long v) { this.totalNotificaciones = v; }
}
