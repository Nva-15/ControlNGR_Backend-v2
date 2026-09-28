package com.example.ControlNGR.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo temporal ante contraseñas incorrectas repetidas (fuerza bruta): tras
 * {@link #MAX_INTENTOS} fallos seguidos para el mismo usuario, se bloquea el ingreso
 * {@link #BLOQUEO} minutos. Un ingreso correcto reinicia el contador. Es en memoria:
 * se reinicia con el sistema, suficiente para frenar ataques automatizados.
 */
@Component
public class LimiteIntentosLogin {

    public static final int MAX_INTENTOS = 5;
    public static final Duration BLOQUEO = Duration.ofMinutes(15);
    /** Tope de usuarios en memoria para que nombres inventados no llenen la RAM. */
    private static final int MAX_REGISTROS = 10_000;

    private record Estado(int fallos, Instant bloqueadoHasta, Instant ultimo) {}

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    private static String clave(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Minutos que faltan si el usuario esta bloqueado; 0 si puede intentar. */
    public long minutosBloqueado(String username) {
        Estado e = estados.get(clave(username));
        if (e == null || e.bloqueadoHasta() == null) return 0;
        Duration resta = Duration.between(Instant.now(), e.bloqueadoHasta());
        if (resta.isNegative() || resta.isZero()) return 0;
        return Math.max(1, (resta.getSeconds() + 59) / 60);
    }

    public void registrarFallo(String username) {
        Instant ahora = Instant.now();
        if (estados.size() > MAX_REGISTROS) limpiar(ahora);
        estados.compute(clave(username), (k, e) -> {
            // Tras un bloqueo cumplido, o 15 minutos sin fallos, se empieza de cero
            int previos = (e == null || (e.bloqueadoHasta() != null && ahora.isAfter(e.bloqueadoHasta()))
                    || Duration.between(e.ultimo(), ahora).compareTo(BLOQUEO) > 0) ? 0 : e.fallos();
            int fallos = previos + 1;
            return new Estado(fallos, fallos >= MAX_INTENTOS ? ahora.plus(BLOQUEO) : null, ahora);
        });
    }

    public void registrarExito(String username) {
        estados.remove(clave(username));
    }

    private void limpiar(Instant ahora) {
        estados.entrySet().removeIf(en -> Duration.between(en.getValue().ultimo(), ahora).compareTo(BLOQUEO) > 0);
    }
}
