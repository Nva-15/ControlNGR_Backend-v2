package com.example.ControlNGR.dto;

public class RespuestaEventoRequestDTO {
    private Integer eventoId;
    private Integer empleadoId;
    private Integer opcionId;          // Para encuestas
    private Boolean respuestaSiNo;     // Para SI/NO
    private String confirmacionAsistencia; // CONFIRMADO, NO_ASISTIRE, PENDIENTE
    private String comentario;

    // Getters y Setters
    public Integer getEventoId() { return eventoId; }
    public void setEventoId(Integer eventoId) { this.eventoId = eventoId; }

    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public Integer getOpcionId() { return opcionId; }
    public void setOpcionId(Integer opcionId) { this.opcionId = opcionId; }

    public Boolean getRespuestaSiNo() { return respuestaSiNo; }
    public void setRespuestaSiNo(Boolean respuestaSiNo) { this.respuestaSiNo = respuestaSiNo; }

    public String getConfirmacionAsistencia() { return confirmacionAsistencia; }
    public void setConfirmacionAsistencia(String confirmacionAsistencia) {
        this.confirmacionAsistencia = confirmacionAsistencia;
    }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
}
