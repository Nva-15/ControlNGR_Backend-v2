package com.example.ControlNGR.dto;

import com.example.ControlNGR.entity.FeriadoLaborado;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FeriadoLaboradoDTO(Integer id, Integer empleadoId, String empleadoNombre, LocalDate fecha,
                                 String feriado, BigDecimal diasOtorgados, String estado, String observacion) {
    public static FeriadoLaboradoDTO de(FeriadoLaborado f) {
        return new FeriadoLaboradoDTO(f.getId(), f.getEmpleado().getId(), f.getEmpleado().getNombre(), f.getFecha(),
                f.getFeriado().getDescripcion(), f.getDiasOtorgados(), f.getEstado(), f.getObservacion());
    }
}
