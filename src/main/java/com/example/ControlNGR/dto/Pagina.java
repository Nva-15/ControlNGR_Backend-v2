package com.example.ControlNGR.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados: el servidor filtra y entrega solo lo que la pantalla va a mostrar. */
public record Pagina<T>(List<T> contenido, long total, int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 20;
    /** Máximo por página en pantalla; las exportaciones (Excel/PDF) pueden pedir hasta {@link #MAXIMO_EXPORTAR}. */
    public static final int MAXIMO = 100;
    public static final int MAXIMO_EXPORTAR = 5000;

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> mapeo) {
        return new Pagina<>(page.getContent().stream().map(mapeo).toList(), page.getTotalElements(), page.getNumber(), page.getSize());
    }

    public static <E, T> Pagina<T> de(Page<E> page, List<T> contenido) {
        return new Pagina<>(contenido, page.getTotalElements(), page.getNumber(), page.getSize());
    }

    /** Página pedida, ordenada por nombre. Un tamaño mayor a {@link #MAXIMO} solo se acepta para exportar. */
    public static PageRequest pedido(Integer pagina, Integer tamano, boolean exportar) {
        int max = exportar ? MAXIMO_EXPORTAR : MAXIMO;
        int t = tamano == null || tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, max);
        int p = pagina == null || pagina < 0 ? 0 : pagina;
        return PageRequest.of(p, t, Sort.by("nombre").ascending().and(Sort.by("id")));
    }

    /** Texto de búsqueda para LIKE (null si está vacío). */
    public static String patron(String q) {
        if (q == null || q.isBlank()) return null;
        String limpio = q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + limpio + "%";
    }
}
