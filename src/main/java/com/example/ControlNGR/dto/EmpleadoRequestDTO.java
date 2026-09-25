package com.example.ControlNGR.dto;

import java.time.LocalDate;

/**
 * Datos para crear/editar un empleado. Mantiene los mismos nombres de campo que
 * el JSON que ya envia el frontend (incluye username, password y rol del usuario).
 */
public class EmpleadoRequestDTO {
    private Integer id;
    private String dni;
    private String nombre;
    private String cargo;
    private String nivel;
    private Integer departamentoId;
    private String email;
    private String descripcion;
    private String hobby;
    private LocalDate cumpleanos;
    private LocalDate ingreso;
    private String foto;
    private Boolean activo;
    private String identificador;
    private String username;
    private String password;
    private String rol;
    private Boolean usuarioActivo;

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
    public Integer getDepartamentoId() { return departamentoId; }
    public void setDepartamentoId(Integer departamentoId) { this.departamentoId = departamentoId; }
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
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public Boolean getUsuarioActivo() { return usuarioActivo; }
    public void setUsuarioActivo(Boolean usuarioActivo) { this.usuarioActivo = usuarioActivo; }
}
