package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.RespuestaEvento;
import java.time.LocalDateTime;

public class RespuestaEventoResponseDTO {
    private Integer id;
    private Integer eventoId;
    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoFoto;
    private Integer opcionId;
    private String opcionTexto;
    private Boolean respuestaSiNo;
    private String confirmacionAsistencia;
    private String comentario;
    private LocalDateTime fechaRespuesta;

    public RespuestaEventoResponseDTO() {}

    public RespuestaEventoResponseDTO(RespuestaEvento respuesta) {
        this.id = respuesta.getId();
        this.eventoId = respuesta.getEvento().getId();
        this.empleadoId = respuesta.getEmpleado().getId();
        this.empleadoNombre = respuesta.getEmpleado().getNombre();
        this.empleadoFoto = respuesta.getEmpleado().getFoto();
        if (respuesta.getOpcion() != null) {
            this.opcionId = respuesta.getOpcion().getId();
            this.opcionTexto = respuesta.getOpcion().getTextoOpcion();
        }
        this.respuestaSiNo = respuesta.getRespuestaSiNo();
        this.confirmacionAsistencia = respuesta.getConfirmacionAsistencia() != null
            ? respuesta.getConfirmacionAsistencia().name() : null;
        this.comentario = respuesta.getComentario();
        this.fechaRespuesta = respuesta.getFechaRespuesta();
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getEventoId() { return eventoId; }
    public void setEventoId(Integer eventoId) { this.eventoId = eventoId; }

    public Integer getEmpleadoId() { return empleadoId; }
    public void setEmpleadoId(Integer empleadoId) { this.empleadoId = empleadoId; }

    public String getEmpleadoNombre() { return empleadoNombre; }
    public void setEmpleadoNombre(String empleadoNombre) { this.empleadoNombre = empleadoNombre; }

    public String getEmpleadoFoto() { return empleadoFoto; }
    public void setEmpleadoFoto(String empleadoFoto) { this.empleadoFoto = empleadoFoto; }

    public Integer getOpcionId() { return opcionId; }
    public void setOpcionId(Integer opcionId) { this.opcionId = opcionId; }

    public String getOpcionTexto() { return opcionTexto; }
    public void setOpcionTexto(String opcionTexto) { this.opcionTexto = opcionTexto; }

    public Boolean getRespuestaSiNo() { return respuestaSiNo; }
    public void setRespuestaSiNo(Boolean respuestaSiNo) { this.respuestaSiNo = respuestaSiNo; }

    public String getConfirmacionAsistencia() { return confirmacionAsistencia; }
    public void setConfirmacionAsistencia(String confirmacionAsistencia) {
        this.confirmacionAsistencia = confirmacionAsistencia;
    }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getFechaRespuesta() { return fechaRespuesta; }
    public void setFechaRespuesta(LocalDateTime fechaRespuesta) { this.fechaRespuesta = fechaRespuesta; }
}
