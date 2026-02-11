package com.example.ControlNGR.dto;

import java.util.List;

public class EstadisticasEventoDTO {
    private Integer eventoId;
    private String tipoEvento;
    private Long totalRespuestas;
    private Long totalEmpleados;
    private Double porcentajeParticipacion;

    // Para SI/NO
    private Long respuestasSi;
    private Long respuestasNo;

    // Para ASISTENCIA
    private Long confirmados;
    private Long noAsistiran;
    private Long pendientes;

    // Para ENCUESTA
    private List<OpcionEstadisticaDTO> opcionesEstadisticas;

    // Getters y Setters
    public Integer getEventoId() { return eventoId; }
    public void setEventoId(Integer eventoId) { this.eventoId = eventoId; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public Long getTotalRespuestas() { return totalRespuestas; }
    public void setTotalRespuestas(Long totalRespuestas) { this.totalRespuestas = totalRespuestas; }

    public Long getTotalEmpleados() { return totalEmpleados; }
    public void setTotalEmpleados(Long totalEmpleados) { this.totalEmpleados = totalEmpleados; }

    public Double getPorcentajeParticipacion() { return porcentajeParticipacion; }
    public void setPorcentajeParticipacion(Double porcentajeParticipacion) {
        this.porcentajeParticipacion = porcentajeParticipacion;
    }

    public Long getRespuestasSi() { return respuestasSi; }
    public void setRespuestasSi(Long respuestasSi) { this.respuestasSi = respuestasSi; }

    public Long getRespuestasNo() { return respuestasNo; }
    public void setRespuestasNo(Long respuestasNo) { this.respuestasNo = respuestasNo; }

    public Long getConfirmados() { return confirmados; }
    public void setConfirmados(Long confirmados) { this.confirmados = confirmados; }

    public Long getNoAsistiran() { return noAsistiran; }
    public void setNoAsistiran(Long noAsistiran) { this.noAsistiran = noAsistiran; }

    public Long getPendientes() { return pendientes; }
    public void setPendientes(Long pendientes) { this.pendientes = pendientes; }

    public List<OpcionEstadisticaDTO> getOpcionesEstadisticas() { return opcionesEstadisticas; }
    public void setOpcionesEstadisticas(List<OpcionEstadisticaDTO> opcionesEstadisticas) {
        this.opcionesEstadisticas = opcionesEstadisticas;
    }

    public static class OpcionEstadisticaDTO {
        private Integer opcionId;
        private String textoOpcion;
        private Long votos;
        private Double porcentaje;

        public Integer getOpcionId() { return opcionId; }
        public void setOpcionId(Integer opcionId) { this.opcionId = opcionId; }

        public String getTextoOpcion() { return textoOpcion; }
        public void setTextoOpcion(String textoOpcion) { this.textoOpcion = textoOpcion; }

        public Long getVotos() { return votos; }
        public void setVotos(Long votos) { this.votos = votos; }

        public Double getPorcentaje() { return porcentaje; }
        public void setPorcentaje(Double porcentaje) { this.porcentaje = porcentaje; }
    }
}
