package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "horarios_semanales")
public class HorarioSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "nombre", length = 100)
    private String nombre;

    @Column(name = "estado", length = 20)
    private String estado; // borrador, activo, historico

    @ManyToOne
    @JoinColumn(name = "creado_por_id")
    private Empleado creadoPor;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "horarioSemanal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HorarioSemanalDetalle> detalles = new ArrayList<>();

    // Constructores
    public HorarioSemanal() {
        this.fechaCreacion = LocalDateTime.now();
        this.estado = "borrador";
    }

    public HorarioSemanal(LocalDate fechaInicio, LocalDate fechaFin, Empleado creadoPor) {
        this();
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.creadoPor = creadoPor;
        this.generarNombre();
    }

    // Generar nombre automático
    public void generarNombre() {
        if (fechaInicio != null && fechaFin != null) {
            String inicio = String.format("%02d/%02d", fechaInicio.getDayOfMonth(), fechaInicio.getMonthValue());
            String fin = String.format("%02d/%02d", fechaFin.getDayOfMonth(), fechaFin.getMonthValue());
            this.nombre = "Semana del " + inicio + " al " + fin;
        }
    }

    // Getters y Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
        generarNombre();
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
        generarNombre();
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Empleado getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(Empleado creadoPor) {
        this.creadoPor = creadoPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<HorarioSemanalDetalle> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<HorarioSemanalDetalle> detalles) {
        this.detalles = detalles;
    }

    // Helper method para agregar detalle
    public void addDetalle(HorarioSemanalDetalle detalle) {
        detalles.add(detalle);
        detalle.setHorarioSemanal(this);
    }

    public void removeDetalle(HorarioSemanalDetalle detalle) {
        detalles.remove(detalle);
        detalle.setHorarioSemanal(null);
    }
}
