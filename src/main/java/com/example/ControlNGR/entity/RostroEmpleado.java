package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Una muestra del rostro de un empleado: descriptor de 128 valores calculado con face-api.js. */
@Entity
@Table(name = "rostros_empleado")
public class RostroEmpleado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    /** Arreglo JSON de 128 numeros. */
    @Column(name = "descriptor", nullable = false, columnDefinition = "TEXT")
    private String descriptor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public RostroEmpleado() {}

    public RostroEmpleado(Empleado empleado, String descriptor, Usuario registradoPor) {
        this.empleado = empleado;
        this.descriptor = descriptor;
        this.registradoPor = registradoPor;
    }

    public Integer getId() { return id; }
    public Empleado getEmpleado() { return empleado; }
    public String getDescriptor() { return descriptor; }
    public Usuario getRegistradoPor() { return registradoPor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
