package com.example.ControlNGR.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Fechas de feriados al copiarlos a otro año. Casi todos caen el mismo dia cada año;
 * Jueves y Viernes Santo dependen de la Pascua y se recalculan.
 */
public final class FeriadoFechas {

    private FeriadoFechas() {}

    /** Domingo de Pascua (calendario gregoriano, algoritmo anonimo de Meeus/Jones/Butcher). */
    public static LocalDate domingoDePascua(int anio) {
        int a = anio % 19, b = anio / 100, c = anio % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31, dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anio, mes, dia);
    }

    /** Dias respecto del Domingo de Pascua si el feriado es movil (Jueves Santo -3, Viernes Santo -2); si no, null. */
    public static Integer desfaseSemanaSanta(String descripcion) {
        String d = normalizar(descripcion);
        if (d.contains("jueves santo")) return -3;
        if (d.contains("viernes santo")) return -2;
        return null;
    }

    /**
     * Fecha del feriado en el año destino: Semana Santa segun la Pascua; el resto, mismo dia y mes.
     * Devuelve null si la fecha no existe ese año (29 de febrero).
     */
    public static LocalDate enAnio(LocalDate fecha, String descripcion, int anioDestino) {
        Integer desfase = desfaseSemanaSanta(descripcion);
        if (desfase != null) return domingoDePascua(anioDestino).plusDays(desfase);
        MonthDay md = MonthDay.from(fecha);
        if (md.getMonth() == Month.FEBRUARY && md.getDayOfMonth() == 29 && !java.time.Year.isLeap(anioDestino)) return null;
        return md.atYear(anioDestino);
    }

    static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
