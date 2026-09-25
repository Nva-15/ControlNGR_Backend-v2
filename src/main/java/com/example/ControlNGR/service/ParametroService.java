package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.ParametroSistema;
import com.example.ControlNGR.repository.ParametroSistemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lectura y escritura de los parametros configurables desde el panel admin. */
@Service
public class ParametroService {

    public static final String DIAS_POR_FERIADO_LABORADO = "DIAS_POR_FERIADO_LABORADO";
    public static final String DIAS_VACACIONES_POR_ANIO = "DIAS_VACACIONES_POR_ANIO";
    public static final String VACACIONES_ABONO_DESDE = "VACACIONES_ABONO_DESDE";
    public static final String VALIDAR_IP_MARCACION = "VALIDAR_IP_MARCACION";
    public static final String TOLERANCIA_TARDANZA_MINUTOS = "TOLERANCIA_TARDANZA_MINUTOS";
    public static final String EVIDENCIA_MAX_MB = "EVIDENCIA_MAX_MB";

    private final ParametroSistemaRepository repository;

    public ParametroService(ParametroSistemaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ParametroSistema> listar() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public String texto(String clave, String porDefecto) {
        return repository.findById(clave).map(ParametroSistema::getValor).orElse(porDefecto);
    }

    public BigDecimal decimal(String clave, BigDecimal porDefecto) {
        try {
            return new BigDecimal(texto(clave, porDefecto.toPlainString()).trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    public int entero(String clave, int porDefecto) {
        return decimal(clave, BigDecimal.valueOf(porDefecto)).intValue();
    }

    public boolean booleano(String clave, boolean porDefecto) {
        return Boolean.parseBoolean(texto(clave, String.valueOf(porDefecto)).trim());
    }

    public LocalDate fecha(String clave, LocalDate porDefecto) {
        try {
            return LocalDate.parse(texto(clave, porDefecto.toString()).trim());
        } catch (Exception e) {
            return porDefecto;
        }
    }

    @Transactional
    public ParametroSistema actualizar(String clave, String valor) {
        ParametroSistema p = repository.findById(clave)
                .orElseThrow(() -> new IllegalArgumentException("Parametro no encontrado: " + clave));
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El valor es requerido");
        }
        valor = valor.trim();
        switch (p.getTipoDato()) {
            case "NUMERO" -> {
                try {
                    if (new BigDecimal(valor).signum() < 0) throw new IllegalArgumentException("El valor no puede ser negativo");
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("El valor debe ser numerico");
                }
            }
            case "BOOLEANO" -> {
                if (!valor.equalsIgnoreCase("true") && !valor.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("El valor debe ser true o false");
                }
                valor = valor.toLowerCase();
            }
            case "FECHA" -> {
                try {
                    LocalDate.parse(valor);
                } catch (Exception e) {
                    throw new IllegalArgumentException("La fecha debe tener formato AAAA-MM-DD");
                }
            }
            default -> { }
        }
        p.setValor(valor);
        return repository.save(p);
    }
}
