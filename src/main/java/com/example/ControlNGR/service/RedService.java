package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.SegmentoRed;
import com.example.ControlNGR.repository.SegmentoRedRepository;
import com.example.ControlNGR.security.FueraDeRedException;
import com.example.ControlNGR.security.IpMatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Validacion de la IP desde la que se marca asistencia. */
@Service
public class RedService {

    private final SegmentoRedRepository segmentoRepository;
    private final ParametroService parametroService;

    public RedService(SegmentoRedRepository segmentoRepository, ParametroService parametroService) {
        this.segmentoRepository = segmentoRepository;
        this.parametroService = parametroService;
    }

    /**
     * IP del cliente. Si hay un proxy de confianza configurado
     * (server.forward-headers-strategy=native) Tomcat ya la resuelve en getRemoteAddr().
     */
    public String ipCliente(HttpServletRequest request) {
        return request != null ? request.getRemoteAddr() : null;
    }

    @Transactional(readOnly = true)
    public boolean ipPermitida(String ip) {
        if (!parametroService.booleano(ParametroService.VALIDAR_IP_MARCACION, true)) {
            return true;
        }
        List<SegmentoRed> segmentos = segmentoRepository.findByActivoTrue();
        return segmentos.stream().anyMatch(s -> IpMatcher.coincide(ip, s.getPatron()));
    }

    /** Lanza FueraDeRedException si la IP no pertenece a un segmento permitido. */
    public void validarIp(String ip) {
        if (!ipPermitida(ip)) {
            throw new FueraDeRedException(ip);
        }
    }
}
