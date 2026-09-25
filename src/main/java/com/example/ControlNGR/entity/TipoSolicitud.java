package com.example.ControlNGR.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tipos_solicitud")
public class TipoSolicitud {
    public static final String NINGUNO = "NINGUNO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo", nullable = false, length = 30, unique = true)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    /** VACACIONES, COMPENSACION o NINGUNO. */
    @Column(name = "descuenta_de", nullable = false, length = 20)
    private String descuentaDe = NINGUNO;

    @Column(name = "requiere_evidencia", nullable = false)
    private Boolean requiereEvidencia = false;

    @Column(name = "requiere_motivo_licencia", nullable = false)
    private Boolean requiereMotivoLicencia = false;

    @Column(name = "tipo_dia_horario", nullable = false, length = 20)
    private String tipoDiaHorario;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Saldo del que descuenta, o null si no descuenta. */
    public TipoSaldo getTipoSaldo() {
        return descuentaDe == null || NINGUNO.equals(descuentaDe) ? null : TipoSaldo.valueOf(descuentaDe);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getDescuentaDe() { return descuentaDe; }
    public void setDescuentaDe(String descuentaDe) { this.descuentaDe = descuentaDe; }

    public Boolean getRequiereEvidencia() { return requiereEvidencia; }
    public void setRequiereEvidencia(Boolean requiereEvidencia) { this.requiereEvidencia = requiereEvidencia; }

    public Boolean getRequiereMotivoLicencia() { return requiereMotivoLicencia; }
    public void setRequiereMotivoLicencia(Boolean requiereMotivoLicencia) { this.requiereMotivoLicencia = requiereMotivoLicencia; }

    public String getTipoDiaHorario() { return tipoDiaHorario; }
    public void setTipoDiaHorario(String tipoDiaHorario) { this.tipoDiaHorario = tipoDiaHorario; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
