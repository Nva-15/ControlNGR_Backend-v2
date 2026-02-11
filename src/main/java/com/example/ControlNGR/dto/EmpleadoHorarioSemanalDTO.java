package com.example.ControlNGR.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class EmpleadoHorarioSemanalDTO {

    private Integer empleadoId;
    private String empleadoNombre;
    private String empleadoRol;
    private String empleadoCargo;
    private Map<String, DetalleHorarioDiaDTO> dias; // Clave: fecha ISO (yyyy-MM-dd)

    // Constructores
    public EmpleadoHorarioSemanalDTO() {
        this.dias = new LinkedHashMap<>(); // Mantener orden de inserción
    }

    public EmpleadoHorarioSemanalDTO(Integer empleadoId, String empleadoNombre,
                                      String empleadoRol, String empleadoCargo) {
        this();
        this.empleadoId = empleadoId;
        this.empleadoNombre = empleadoNombre;
        this.empleadoRol = empleadoRol;
        this.empleadoCargo = empleadoCargo;
    }

    // Agregar día al mapa
    public void agregarDia(String fechaKey, DetalleHorarioDiaDTO detalle) {
        this.dias.put(fechaKey, detalle);
    }

    // Getters y Setters
    public Integer getEmpleadoId() {
        return empleadoId;
    }

    public void setEmpleadoId(Integer empleadoId) {
        this.empleadoId = empleadoId;
    }

    public String getEmpleadoNombre() {
        return empleadoNombre;
    }

    public void setEmpleadoNombre(String empleadoNombre) {
        this.empleadoNombre = empleadoNombre;
    }

    public String getEmpleadoRol() {
        return empleadoRol;
    }

    public void setEmpleadoRol(String empleadoRol) {
        this.empleadoRol = empleadoRol;
    }

    public String getEmpleadoCargo() {
        return empleadoCargo;
    }

    public void setEmpleadoCargo(String empleadoCargo) {
        this.empleadoCargo = empleadoCargo;
    }

    public Map<String, DetalleHorarioDiaDTO> getDias() {
        return dias;
    }

    public void setDias(Map<String, DetalleHorarioDiaDTO> dias) {
        this.dias = dias;
    }
}
