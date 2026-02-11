package com.example.ControlNGR.dto;

import java.time.LocalDateTime;
import java.util.List;

public class EventoRequestDTO {
    private String titulo;
    private String descripcion;
    private String tipoEvento; // ENCUESTA, SI_NO, ASISTENCIA, INFORMATIVO
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private List<String> rolesVisibles;
    private Boolean permiteComentarios = true;
    private Boolean requiereRespuesta = false;
    private List<String> opciones; // Para encuestas de opción múltiple
    private Integer creadoPorId;

    // Getters y Setters
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }

    public List<String> getRolesVisibles() { return rolesVisibles; }
    public void setRolesVisibles(List<String> rolesVisibles) { this.rolesVisibles = rolesVisibles; }

    public Boolean getPermiteComentarios() { return permiteComentarios; }
    public void setPermiteComentarios(Boolean permiteComentarios) { this.permiteComentarios = permiteComentarios; }

    public Boolean getRequiereRespuesta() { return requiereRespuesta; }
    public void setRequiereRespuesta(Boolean requiereRespuesta) { this.requiereRespuesta = requiereRespuesta; }

    public List<String> getOpciones() { return opciones; }
    public void setOpciones(List<String> opciones) { this.opciones = opciones; }

    public Integer getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(Integer creadoPorId) { this.creadoPorId = creadoPorId; }
}
