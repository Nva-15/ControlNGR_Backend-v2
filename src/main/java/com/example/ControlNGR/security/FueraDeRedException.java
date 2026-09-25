package com.example.ControlNGR.security;

/** Marcacion desde una IP que no pertenece a los segmentos permitidos. */
public class FueraDeRedException extends RuntimeException {
    private final String ip;

    public FueraDeRedException(String ip) {
        super("Está fuera de red. No se permite marcar asistencia desde este equipo (IP " + ip + ").");
        this.ip = ip;
    }

    public String getIp() { return ip; }
}
