package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Departamento;
import com.example.ControlNGR.entity.Empleado;
import com.example.ControlNGR.entity.MarcacionEquipo;
import com.example.ControlNGR.entity.RostroEmpleado;
import com.example.ControlNGR.repository.DepartamentoRepository;
import com.example.ControlNGR.repository.EmpleadoRepository;
import com.example.ControlNGR.repository.MarcacionEquipoRepository;
import com.example.ControlNGR.repository.RostroEmpleadoRepository;
import com.example.ControlNGR.security.RostroException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El modo del equipo decide si la marcación usa la cámara, no si la persona tiene rostro registrado. */
class ModoMarcacionTest {

    private MarcacionEquipoRepository marcacionRepo;
    private RostroEmpleadoRepository rostroRepo;
    private ParametroService parametros;
    private ModoMarcacionService modo;
    private FacialService facial;

    @BeforeEach
    void preparar() {
        marcacionRepo = mock(MarcacionEquipoRepository.class);
        rostroRepo = mock(RostroEmpleadoRepository.class);
        parametros = mock(ParametroService.class);
        when(parametros.booleano(eq(FacialService.MARCACION_FACIAL_OBLIGATORIA), anyBoolean())).thenReturn(false);
        when(parametros.decimal(eq(FacialService.UMBRAL_FACIAL), any())).thenReturn(new BigDecimal("0.5"));
        modo = new ModoMarcacionService(marcacionRepo, mock(DepartamentoRepository.class), mock(EmpleadoRepository.class),
                rostroRepo, parametros);
        facial = new FacialService(rostroRepo, mock(EmpleadoRepository.class), parametros, modo);
    }

    private static double[] vector(double v) {
        double[] d = new double[FacialService.DIMENSION];
        Arrays.fill(d, v);
        return d;
    }

    private Empleado empleado(int id, Integer depto, String modoEquipo) {
        Empleado e = new Empleado();
        e.setId(id);
        if (depto != null) {
            Departamento d = new Departamento();
            d.setId(depto);
            e.setDepartamento(d);
            if (modoEquipo != null) {
                MarcacionEquipo m = new MarcacionEquipo();
                m.setDepartamento(d);
                m.setModo(modoEquipo);
                when(marcacionRepo.findByDepartamentoId(depto)).thenReturn(Optional.of(m));
            }
        }
        return e;
    }

    private void conRostro(Empleado e) throws Exception {
        RostroEmpleado r = new RostroEmpleado(e, new ObjectMapper().writeValueAsString(vector(0.1)), null);
        when(rostroRepo.findByEmpleadoId(e.getId())).thenReturn(List.of(r));
    }

    @Test
    void equipoConBotonMarcaSinCamaraAunqueTengaRostro() throws Exception {
        Empleado e = empleado(1, 2, MarcacionEquipo.BOTON);
        conRostro(e);
        assertEquals("manual", facial.verificar(e, null).metodo());
        assertEquals(false, facial.estado(e).get("obligatorio"));
    }

    @Test
    void equipoFacialExigeRostroRegistradoYCaptura() throws Exception {
        Empleado sinRostro = empleado(1, 3, MarcacionEquipo.FACIAL);
        when(rostroRepo.findByEmpleadoId(1)).thenReturn(List.of());
        assertEquals("NO_REGISTRADO", assertThrows(RostroException.class, () -> facial.verificar(sinRostro, vector(0.1))).getCodigo());

        Empleado conRostro = empleado(2, 3, MarcacionEquipo.FACIAL);
        conRostro(conRostro);
        assertEquals("FALTA_ROSTRO", assertThrows(RostroException.class, () -> facial.verificar(conRostro, null)).getCodigo());
        assertEquals("NO_COINCIDE", assertThrows(RostroException.class, () -> facial.verificar(conRostro, vector(0.3))).getCodigo());
        assertEquals("facial", facial.verificar(conRostro, vector(0.1)).metodo());
        assertEquals(true, facial.estado(conRostro).get("obligatorio"));
    }

    @Test
    void sinEquipoConfiguradoUsaElParametro() {
        Empleado sinDepto = empleado(1, null, null);
        Empleado deptoNuevo = empleado(2, 9, null);
        assertEquals(MarcacionEquipo.BOTON, modo.modo(sinDepto));
        assertEquals(MarcacionEquipo.BOTON, modo.modo(deptoNuevo));
        when(parametros.booleano(eq(FacialService.MARCACION_FACIAL_OBLIGATORIA), anyBoolean())).thenReturn(true);
        assertEquals(MarcacionEquipo.FACIAL, modo.modo(sinDepto));
        assertEquals(MarcacionEquipo.FACIAL, modo.modo(deptoNuevo));
    }
}
