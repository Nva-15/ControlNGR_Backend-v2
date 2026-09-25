package com.example.ControlNGR.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Datos personales y laborales. Las credenciales y el rol viven en {@link Usuario}.
 * Los getters getRol/getUsername/getUsuarioActivo se mantienen para que el JSON
 * que recibe el frontend siga teniendo la misma forma.
 */
@Entity
@Table(name = "empleados")
public class Empleado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "dni", nullable = false, length = 20, unique = true)
    private String dni;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    @Column(name = "nivel", length = 20)
    private String nivel;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "hobby", columnDefinition = "TEXT")
    private String hobby;

    @Column(name = "cumpleanos")
    private LocalDate cumpleanos;

    @Column(name = "ingreso")
    private LocalDate ingreso;

    @Column(name = "foto", length = 255)
    private String foto = "img/perfil.png";

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "identificador", length = 50, unique = true)
    private String identificador;

    @JsonIgnore
    @OneToOne(mappedBy = "empleado", fetch = FetchType.EAGER)
    private Usuario usuario;

    public Empleado() {}

    // ---- Datos derivados del usuario (solo lectura) ----
    @JsonProperty("rol")
    public String getRol() {
        return usuario != null ? usuario.getRol() : null;
    }

    @JsonProperty("username")
    public String getUsername() {
        return usuario != null ? usuario.getUsername() : null;
    }

    @JsonProperty("usuarioActivo")
    public Boolean getUsuarioActivo() {
        return usuario != null ? usuario.getActivo() : Boolean.FALSE;
    }

    @JsonProperty("departamentoId")
    public Integer getDepartamentoId() {
        return departamento != null ? departamento.getId() : null;
    }

    @JsonProperty("departamentoNombre")
    public String getDepartamentoNombre() {
        return departamento != null ? departamento.getNombre() : null;
    }

    // ---- Getters y Setters ----
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }

    public Departamento getDepartamento() { return departamento; }
    public void setDepartamento(Departamento departamento) { this.departamento = departamento; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getHobby() { return hobby; }
    public void setHobby(String hobby) { this.hobby = hobby; }

    public LocalDate getCumpleanos() { return cumpleanos; }
    public void setCumpleanos(LocalDate cumpleanos) { this.cumpleanos = cumpleanos; }

    public LocalDate getIngreso() { return ingreso; }
    public void setIngreso(LocalDate ingreso) { this.ingreso = ingreso; }

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
