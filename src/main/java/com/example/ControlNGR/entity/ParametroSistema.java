package com.example.ControlNGR.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "parametros_sistema")
public class ParametroSistema {
    @Id
    @Column(name = "clave", length = 60)
    private String clave;

    @Column(name = "valor", nullable = false, length = 255)
    private String valor;

    @Column(name = "tipo_dato", nullable = false, length = 20)
    private String tipoDato = "TEXTO";

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getTipoDato() { return tipoDato; }
    public void setTipoDato(String tipoDato) { this.tipoDato = tipoDato; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
