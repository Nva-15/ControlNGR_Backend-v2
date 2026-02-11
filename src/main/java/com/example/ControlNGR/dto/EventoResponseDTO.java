package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.Evento;
import com.example.ControlNGR.entity.OpcionEvento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class EventoResponseDTO {
    private Integer id;
    private String titulo;
    private String descripcion;
    private String tipoEvento;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String estado;
    private List<String> rolesVisibles;
    private Boolean permiteComentarios;
    private Boolean requiereRespuesta;
    private String creadoPorNombre;
    private Integer creadoPorId;
    private LocalDateTime fechaCreacion;
    private List<OpcionEventoDTO> opciones;
    private Long totalRespuestas;
    private Long totalComentarios;
    private Boolean yaRespondio;

    public EventoResponseDTO() {}

    public EventoResponseDTO(Evento evento) {
        this.id = evento.getId();
        this.titulo = evento.getTitulo();
        this.descripcion = evento.getDescripcion();
        this.tipoEvento = evento.getTipoEvento().name();
        this.fechaInicio = evento.getFechaInicio();
        this.fechaFin = evento.getFechaFin();
        this.estado = evento.getEstado().name();
        this.rolesVisibles = evento.getRolesVisiblesList();
        this.permiteComentarios = evento.getPermiteComentarios();
        this.requiereRespuesta = evento.getRequiereRespuesta();
        this.creadoPorNombre = evento.getCreadoPor() != null ? evento.getCreadoPor().getNombre() : null;
        this.creadoPorId = evento.getCreadoPor() != null ? evento.getCreadoPor().getId() : null;
        this.fechaCreacion = evento.getFechaCreacion();

        if (evento.getOpciones() != null) {
            this.opciones = evento.getOpciones().stream()
                .map(OpcionEventoDTO::new)
                .collect(Collectors.toList());
        }
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

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

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public List<String> getRolesVisibles() { return rolesVisibles; }
    public void setRolesVisibles(List<String> rolesVisibles) { this.rolesVisibles = rolesVisibles; }

    public Boolean getPermiteComentarios() { return permiteComentarios; }
    public void setPermiteComentarios(Boolean permiteComentarios) { this.permiteComentarios = permiteComentarios; }

    public Boolean getRequiereRespuesta() { return requiereRespuesta; }
    public void setRequiereRespuesta(Boolean requiereRespuesta) { this.requiereRespuesta = requiereRespuesta; }

    public String getCreadoPorNombre() { return creadoPorNombre; }
    public void setCreadoPorNombre(String creadoPorNombre) { this.creadoPorNombre = creadoPorNombre; }

    public Integer getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(Integer creadoPorId) { this.creadoPorId = creadoPorId; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public List<OpcionEventoDTO> getOpciones() { return opciones; }
    public void setOpciones(List<OpcionEventoDTO> opciones) { this.opciones = opciones; }

    public Long getTotalRespuestas() { return totalRespuestas; }
    public void setTotalRespuestas(Long totalRespuestas) { this.totalRespuestas = totalRespuestas; }

    public Long getTotalComentarios() { return totalComentarios; }
    public void setTotalComentarios(Long totalComentarios) { this.totalComentarios = totalComentarios; }

    public Boolean getYaRespondio() { return yaRespondio; }
    public void setYaRespondio(Boolean yaRespondio) { this.yaRespondio = yaRespondio; }

    // DTO interno para opciones
    public static class OpcionEventoDTO {
        private Integer id;
        private String textoOpcion;
        private Integer orden;
        private Long conteoVotos;

        public OpcionEventoDTO() {}

        public OpcionEventoDTO(OpcionEvento opcion) {
            this.id = opcion.getId();
            this.textoOpcion = opcion.getTextoOpcion();
            this.orden = opcion.getOrden();
        }

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getTextoOpcion() { return textoOpcion; }
        public void setTextoOpcion(String textoOpcion) { this.textoOpcion = textoOpcion; }

        public Integer getOrden() { return orden; }
        public void setOrden(Integer orden) { this.orden = orden; }

        public Long getConteoVotos() { return conteoVotos; }
        public void setConteoVotos(Long conteoVotos) { this.conteoVotos = conteoVotos; }
    }
}
