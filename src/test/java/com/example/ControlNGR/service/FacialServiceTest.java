package com.example.ControlNGR.service;

import com.example.ControlNGR.security.RostroException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FacialServiceTest {

    private static double[] vector(double valor) {
        double[] d = new double[FacialService.DIMENSION];
        Arrays.fill(d, valor);
        return d;
    }

    @Test
    void distanciaEuclidiana() {
        assertEquals(0.0, FacialService.distancia(vector(0.1), vector(0.1)), 1e-9);
        // 128 dimensiones con diferencia 0.05 -> sqrt(128 * 0.0025) = 0.5657
        assertEquals(Math.sqrt(128 * 0.0025), FacialService.distancia(vector(0.1), vector(0.15)), 1e-9);
    }

    @Test
    void descriptorInvalidoSeRechaza() {
        assertThrows(RostroException.class, () -> FacialService.validarDescriptor(null));
        assertThrows(RostroException.class, () -> FacialService.validarDescriptor(new double[10]));
        double[] conNaN = vector(0.1);
        conNaN[5] = Double.NaN;
        assertThrows(RostroException.class, () -> FacialService.validarDescriptor(conNaN));
        double[] fueraDeRango = vector(0.1);
        fueraDeRango[0] = 50;
        assertThrows(RostroException.class, () -> FacialService.validarDescriptor(fueraDeRango));
        FacialService.validarDescriptor(vector(0.1));
    }
}
