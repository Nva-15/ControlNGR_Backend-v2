package com.example.ControlNGR.dto;

import java.time.LocalDateTime;

public class ComentarioEventoDTO {
    private Integer id;
    private Integer eventoId;
    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoFoto;
    private String comentario;
    private LocalDateTime fechaComentario;

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

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getFechaComentario() { return fechaComentario; }
    public void setFechaComentario(LocalDateTime fechaComentario) { this.fechaComentario = fechaComentario; }
}
