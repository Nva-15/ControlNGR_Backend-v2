package com.example.ControlNGR.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Respaldo generado (manual o programado). El archivo vive en la carpeta de respaldos. */
@Entity
@Table(name = "respaldos")
public class Respaldo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String archivo;

    /** MANUAL o PROGRAMADO. */
    @Column(nullable = false, length = 12)
    private String tipo;

    /** EN_CURSO, COMPLETADO o FALLIDO. */
    @Column(nullable = false, length = 12)
    private String estado;

    @Column(name = "incluye_archivos", nullable = false)
    private Boolean incluyeArchivos = false;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "iniciado_en", nullable = false)
    private LocalDateTime iniciadoEn;

    @Column(name = "finalizado_en")
    private LocalDateTime finalizadoEn;

    @Column(length = 500)
    private String mensaje;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Boolean getIncluyeArchivos() { return incluyeArchivos; }
    public void setIncluyeArchivos(Boolean incluyeArchivos) { this.incluyeArchivos = incluyeArchivos; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public LocalDateTime getIniciadoEn() { return iniciadoEn; }
    public void setIniciadoEn(LocalDateTime iniciadoEn) { this.iniciadoEn = iniciadoEn; }
    public LocalDateTime getFinalizadoEn() { return finalizadoEn; }
    public void setFinalizadoEn(LocalDateTime finalizadoEn) { this.finalizadoEn = finalizadoEn; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
}
