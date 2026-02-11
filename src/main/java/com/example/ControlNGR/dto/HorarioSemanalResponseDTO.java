package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.HorarioSemanal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HorarioSemanalResponseDTO {

    private Integer id;
    private String nombre;
    private String fechaInicio;
    private String fechaFin;
    private String estado;
    private String creadoPor;
    private Integer creadoPorId;
    private String fechaCreacion;
    private List<EmpleadoHorarioSemanalDTO> empleados;

    // Info adicional
    private boolean esSemanaActual;
    private int totalEmpleados;
    private int totalDiasLaborales;
    private int totalDescansos;
    private int totalVacaciones;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // Constructores
    public HorarioSemanalResponseDTO() {
        this.empleados = new ArrayList<>();
    }

    public HorarioSemanalResponseDTO(HorarioSemanal horarioSemanal) {
        this();
        this.id = horarioSemanal.getId();
        this.nombre = horarioSemanal.getNombre();
        this.fechaInicio = horarioSemanal.getFechaInicio().format(DATE_FORMATTER);
        this.fechaFin = horarioSemanal.getFechaFin().format(DATE_FORMATTER);
        this.estado = horarioSemanal.getEstado();
        this.creadoPor = horarioSemanal.getCreadoPor() != null ?
                horarioSemanal.getCreadoPor().getNombre() : null;
        this.creadoPorId = horarioSemanal.getCreadoPor() != null ?
                horarioSemanal.getCreadoPor().getId() : null;
        this.fechaCreacion = horarioSemanal.getFechaCreacion() != null ?
                horarioSemanal.getFechaCreacion().format(DATETIME_FORMATTER) : null;

        // Determinar si es la semana actual
        LocalDate hoy = LocalDate.now();
        this.esSemanaActual = !hoy.isBefore(horarioSemanal.getFechaInicio()) &&
                             !hoy.isAfter(horarioSemanal.getFechaFin());
    }

    // Agregar empleado
    public void agregarEmpleado(EmpleadoHorarioSemanalDTO empleado) {
        this.empleados.add(empleado);
    }

    // Calcular estadísticas
    public void calcularEstadisticas() {
        this.totalEmpleados = empleados.size();
        int laborales = 0;
        int descansos = 0;
        int vacaciones = 0;

        for (EmpleadoHorarioSemanalDTO emp : empleados) {
            for (DetalleHorarioDiaDTO dia : emp.getDias().values()) {
                if (dia != null && dia.getTipoDia() != null) {
                    switch (dia.getTipoDia().toLowerCase()) {
                        case "normal":
                            laborales++;
                            break;
                        case "descanso":
                            descansos++;
                            break;
                        case "vacaciones":
                            vacaciones++;
                            break;
                    }
                }
            }
        }

        this.totalDiasLaborales = laborales;
        this.totalDescansos = descansos;
        this.totalVacaciones = vacaciones;
    }

    // Getters y Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(String fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(String fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(String creadoPor) {
        this.creadoPor = creadoPor;
    }

    public Integer getCreadoPorId() {
        return creadoPorId;
    }

    public void setCreadoPorId(Integer creadoPorId) {
        this.creadoPorId = creadoPorId;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<EmpleadoHorarioSemanalDTO> getEmpleados() {
        return empleados;
    }

    public void setEmpleados(List<EmpleadoHorarioSemanalDTO> empleados) {
        this.empleados = empleados;
    }

    public boolean isEsSemanaActual() {
        return esSemanaActual;
    }

    public void setEsSemanaActual(boolean esSemanaActual) {
        this.esSemanaActual = esSemanaActual;
    }

    public int getTotalEmpleados() {
        return totalEmpleados;
    }

    public void setTotalEmpleados(int totalEmpleados) {
        this.totalEmpleados = totalEmpleados;
    }

    public int getTotalDiasLaborales() {
        return totalDiasLaborales;
    }

    public void setTotalDiasLaborales(int totalDiasLaborales) {
        this.totalDiasLaborales = totalDiasLaborales;
    }

    public int getTotalDescansos() {
        return totalDescansos;
    }

    public void setTotalDescansos(int totalDescansos) {
        this.totalDescansos = totalDescansos;
    }

    public int getTotalVacaciones() {
        return totalVacaciones;
    }

    public void setTotalVacaciones(int totalVacaciones) {
        this.totalVacaciones = totalVacaciones;
    }
}
