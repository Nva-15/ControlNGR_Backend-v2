package com.example.ControlNGR.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

@Entity
@Table(name = "horarios_semanales_detalle")
public class HorarioSemanalDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horario_semanal_id", nullable = false)
    private HorarioSemanal horarioSemanal;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "dia_semana", length = 20)
    private String diaSemana;

    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Column(name = "hora_almuerzo_inicio")
    private LocalTime horaAlmuerzoInicio;

    @Column(name = "hora_almuerzo_fin")
    private LocalTime horaAlmuerzoFin;

    @Column(name = "tipo_dia", length = 20)
    private String tipoDia; // normal, descanso, vacaciones, permiso, compensado

    @Column(name = "turno", length = 20)
    private String turno; // manana, tarde

    @Column(name = "origen_tipo_dia", length = 30)
    private String origenTipoDia; // manual, solicitud_aprobada

    @ManyToOne
    @JoinColumn(name = "solicitud_ref_id")
    private Solicitud solicitudRef;

    // Constructores
    public HorarioSemanalDetalle() {
        this.tipoDia = "normal";
        this.origenTipoDia = "manual";
    }

    public HorarioSemanalDetalle(HorarioSemanal horarioSemanal, Empleado empleado, LocalDate fecha) {
        this();
        this.horarioSemanal = horarioSemanal;
        this.empleado = empleado;
        this.fecha = fecha;
        this.calcularDiaSemana();
    }

    // Calcular día de la semana en español
    public void calcularDiaSemana() {
        if (fecha != null) {
            String diaIngles = fecha.getDayOfWeek().toString().toLowerCase();
            this.diaSemana = traducirDiaAEspanol(diaIngles);
        }
    }

    private String traducirDiaAEspanol(String diaIngles) {
        switch (diaIngles) {
            case "monday": return "lunes";
            case "tuesday": return "martes";
            case "wednesday": return "miercoles";
            case "thursday": return "jueves";
            case "friday": return "viernes";
            case "saturday": return "sabado";
            case "sunday": return "domingo";
            default: return diaIngles;
        }
    }

    // Copiar datos desde un Horario base
    public void copiarDesdeHorarioBase(Horario horarioBase) {
        if (horarioBase != null) {
            this.horaEntrada = horarioBase.getHoraEntrada();
            this.horaSalida = horarioBase.getHoraSalida();
            this.horaAlmuerzoInicio = horarioBase.getHoraAlmuerzoInicio();
            this.horaAlmuerzoFin = horarioBase.getHoraAlmuerzoFin();
            this.tipoDia = horarioBase.getTipoDia() != null ? horarioBase.getTipoDia() : "normal";
            this.turno = horarioBase.getTurno();
            this.origenTipoDia = "manual";
        }
    }

    // Marcar como día de solicitud aprobada
    public void marcarPorSolicitud(Solicitud solicitud) {
        this.solicitudRef = solicitud;
        this.origenTipoDia = "solicitud_aprobada";

        // Determinar tipo de día según el tipo de solicitud
        String tipoSolicitud = solicitud.getTipo().toLowerCase();
        if (tipoSolicitud.contains("vacacion")) {
            this.tipoDia = "vacaciones";
        } else if (tipoSolicitud.contains("descanso")) {
            this.tipoDia = "descanso";
        } else if (tipoSolicitud.contains("permiso")) {
            this.tipoDia = "permiso";
        } else if (tipoSolicitud.contains("compens")) {
            this.tipoDia = "compensado";
        } else {
            this.tipoDia = tipoSolicitud;
        }

        // Limpiar horarios cuando es día no laboral
        this.horaEntrada = null;
        this.horaSalida = null;
        this.horaAlmuerzoInicio = null;
        this.horaAlmuerzoFin = null;
        this.turno = null;
    }

    // Revertir a estado normal (cuando se rechaza una solicitud)
    public void revertirSolicitud() {
        this.solicitudRef = null;
        this.origenTipoDia = "manual";
        this.tipoDia = "normal";
    }

    // Getters y Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public HorarioSemanal getHorarioSemanal() {
        return horarioSemanal;
    }

    public void setHorarioSemanal(HorarioSemanal horarioSemanal) {
        this.horarioSemanal = horarioSemanal;
    }

    public Empleado getEmpleado() {
        return empleado;
    }

    public void setEmpleado(Empleado empleado) {
        this.empleado = empleado;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
        calcularDiaSemana();
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(String diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraEntrada() {
        return horaEntrada;
    }

    public void setHoraEntrada(LocalTime horaEntrada) {
        this.horaEntrada = horaEntrada;
    }

    public LocalTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(LocalTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public LocalTime getHoraAlmuerzoInicio() {
        return horaAlmuerzoInicio;
    }

    public void setHoraAlmuerzoInicio(LocalTime horaAlmuerzoInicio) {
        this.horaAlmuerzoInicio = horaAlmuerzoInicio;
    }

    public LocalTime getHoraAlmuerzoFin() {
        return horaAlmuerzoFin;
    }

    public void setHoraAlmuerzoFin(LocalTime horaAlmuerzoFin) {
        this.horaAlmuerzoFin = horaAlmuerzoFin;
    }

    public String getTipoDia() {
        return tipoDia;
    }

    public void setTipoDia(String tipoDia) {
        this.tipoDia = tipoDia;
    }

    public String getTurno() {
        return turno;
    }

    public void setTurno(String turno) {
        this.turno = turno;
    }

    public String getOrigenTipoDia() {
        return origenTipoDia;
    }

    public void setOrigenTipoDia(String origenTipoDia) {
        this.origenTipoDia = origenTipoDia;
    }

    public Solicitud getSolicitudRef() {
        return solicitudRef;
    }

    public void setSolicitudRef(Solicitud solicitudRef) {
        this.solicitudRef = solicitudRef;
    }
}
