package com.example.ControlNGR.security;

/** Operacion no permitida para el usuario actual (se responde con 403). */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String message) {
        super(message);
    }
}
