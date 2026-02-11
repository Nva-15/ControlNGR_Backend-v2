package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

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
    
    @Column(name = "nivel", length = 20)
    private String nivel;
    
    @Column(name = "username", length = 50, unique = true)
    private String username;
    
    @Column(name = "email", length = 100)
    private String email; // NUEVO: Campo email
    
    @Column(name = "password", length = 255)
    private String password;
    
    @Column(name = "rol", length = 20)
    private String rol = "tecnico";
    
    @Column(name = "usuario_activo")
    private Boolean usuarioActivo = true;
    
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
    
    @Column(name = "activo")
    private Boolean activo = true;
    
    @Column(name = "identificador", length = 50, unique = true)
    private String identificador;
    
    public Empleado() {}
    
    // Getters y Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    
    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }
    
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; } // NUEVO
    public void setEmail(String email) { this.email = email; } // NUEVO
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    
    public Boolean getUsuarioActivo() { return usuarioActivo; }
    public void setUsuarioActivo(Boolean usuarioActivo) { this.usuarioActivo = usuarioActivo; }
    
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
}