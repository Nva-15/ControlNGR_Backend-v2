package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Solicitud;
import com.example.ControlNGR.entity.SolicitudEvidencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Guarda los archivos de sustento (foto o PDF) fuera de la carpeta publica.
 * Solo se descargan a traves de la API, validando permisos.
 */
@Service
public class EvidenciaService {

    private static final Logger logger = LoggerFactory.getLogger(EvidenciaService.class);

    private final Path raiz;
    private final ParametroService parametroService;

    public EvidenciaService(@Value("${app.storage.evidencias}") String ruta, ParametroService parametroService) {
        this.raiz = Paths.get(ruta).toAbsolutePath().normalize();
        this.parametroService = parametroService;
    }

    /** Valida el archivo (tipo real y tamaño) y lo guarda. Devuelve la entidad sin persistir. */
    public SolicitudEvidencia guardar(MultipartFile archivo, Solicitud solicitud) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo de evidencia (foto o PDF)");
        }
        long maxBytes = parametroService.entero(ParametroService.EVIDENCIA_MAX_MB, 10) * 1024L * 1024L;
        if (archivo.getSize() > maxBytes) {
            throw new IllegalArgumentException("El archivo supera el tamaño maximo de "
                    + (maxBytes / (1024 * 1024)) + " MB");
        }

        String[] tipo = detectarTipo(archivo);
        if (tipo == null) {
            throw new IllegalArgumentException("Formato no permitido. Solo se aceptan imagenes JPG/PNG o archivos PDF");
        }

        String nombreArchivo = UUID.randomUUID() + "." + tipo[1];
        Path destino = raiz.resolve(nombreArchivo).normalize();
        try {
            Files.createDirectories(raiz);
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("No se pudo guardar la evidencia: {}", e.getMessage());
            throw new IllegalStateException("No se pudo guardar el archivo de evidencia");
        }

        SolicitudEvidencia ev = new SolicitudEvidencia();
        ev.setSolicitud(solicitud);
        ev.setNombreOriginal(limpiarNombre(archivo.getOriginalFilename(), tipo[1]));
        ev.setNombreArchivo(nombreArchivo);
        ev.setContentType(tipo[0]);
        ev.setTamanoBytes(archivo.getSize());
        return ev;
    }

    public Resource cargar(SolicitudEvidencia evidencia) {
        Path archivo = raiz.resolve(evidencia.getNombreArchivo()).normalize();
        if (!archivo.startsWith(raiz) || !Files.exists(archivo)) {
            throw new IllegalArgumentException("Archivo de evidencia no encontrado");
        }
        return new FileSystemResource(archivo);
    }

    /** Borra el archivo fisico (por ejemplo, si la transaccion de la solicitud fallo). */
    public void eliminarArchivo(String nombreArchivo) {
        try {
            Path archivo = raiz.resolve(nombreArchivo).normalize();
            if (archivo.startsWith(raiz)) {
                Files.deleteIfExists(archivo);
            }
        } catch (IOException e) {
            logger.warn("No se pudo eliminar la evidencia {}: {}", nombreArchivo, e.getMessage());
        }
    }

    /** Detecta el tipo por los primeros bytes (no confia en la extension). */
    private String[] detectarTipo(MultipartFile archivo) {
        byte[] cabecera = new byte[8];
        try (InputStream in = archivo.getInputStream()) {
            int leidos = in.readNBytes(cabecera, 0, cabecera.length);
            if (leidos < 4) return null;
        } catch (IOException e) {
            return null;
        }
        if (cabecera[0] == 0x25 && cabecera[1] == 0x50 && cabecera[2] == 0x44 && cabecera[3] == 0x46) {
            return new String[]{"application/pdf", "pdf"};
        }
        if ((cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8 && (cabecera[2] & 0xFF) == 0xFF) {
            return new String[]{"image/jpeg", "jpg"};
        }
        if ((cabecera[0] & 0xFF) == 0x89 && cabecera[1] == 0x50 && cabecera[2] == 0x4E && cabecera[3] == 0x47) {
            return new String[]{"image/png", "png"};
        }
        return null;
    }

    private String limpiarNombre(String original, String extension) {
        if (original == null || original.isBlank()) return "evidencia." + extension;
        String nombre = Paths.get(original).getFileName().toString().replaceAll("[\\r\\n\"]", "_");
        return nombre.length() > 200 ? nombre.substring(nombre.length() - 200) : nombre;
    }
}
