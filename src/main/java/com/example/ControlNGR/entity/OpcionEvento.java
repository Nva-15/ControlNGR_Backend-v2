package com.example.ControlNGR.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "opciones_evento")
public class OpcionEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "texto_opcion", nullable = false, length = 500)
    private String textoOpcion;

    @Column(name = "orden")
    private Integer orden = 0;

    public OpcionEvento() {}

    public OpcionEvento(String textoOpcion, Integer orden) {
        this.textoOpcion = textoOpcion;
        this.orden = orden;
    }

    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }

    public String getTextoOpcion() { return textoOpcion; }
    public void setTextoOpcion(String textoOpcion) { this.textoOpcion = textoOpcion; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }
}
