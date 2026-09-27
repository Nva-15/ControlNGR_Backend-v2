package com.example.ControlNGR.security;

/**
 * Problema con el reconocimiento facial. El codigo permite al frontend mostrar el mensaje adecuado:
 * NO_REGISTRADO, FALTA_ROSTRO, NO_COINCIDE, DESCRIPTOR_INVALIDO, YA_REGISTRADO, CONSENTIMIENTO,
 * MUESTRAS_INCONSISTENTES, ROSTRO_DE_OTRO.
 */
public class RostroException extends RuntimeException {
    private final String codigo;
    private final int status;

    public RostroException(String codigo, String mensaje, int status) {
        super(mensaje);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() { return codigo; }
    public int getStatus() { return status; }
}
