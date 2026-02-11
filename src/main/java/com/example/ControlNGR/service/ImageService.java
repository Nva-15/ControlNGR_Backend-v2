package com.example.ControlNGR.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 400;
    private static final float JPEG_QUALITY = 0.85f;

    @Value("${app.storage.location}")
    private String storageLocation;

    /**
     * Normaliza la ruta de almacenamiento removiendo el prefijo file: en cualquier formato
     * Soporta: file:///path, file://path, file:/path, file:./path
     */
    private String normalizarRuta() {
        if (storageLocation == null) return "";
        // Remover prefijo file: con cualquier cantidad de slashes (0 a 3)
        return storageLocation
            .replaceFirst("^file:/{0,3}", "")
            .replace("\\", "/");
    }

    private boolean esExtensionValida(String filename) {
        String[] extensionesValidas = {".png", ".jpg", ".jpeg", ".gif", ".bmp"};
        String extension = filename.toLowerCase();
        for (String ext : extensionesValidas) {
            if (extension.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private String detectarFormatoImagen(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "desconocido (archivo muy pequeño)";
        }

        // PNG: 89 50 4E 47
        if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 &&
            bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "PNG";
        }

        // JPEG: FF D8 FF
        if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "JPEG";
        }

        // GIF: 47 49 46
        if (bytes.length >= 3 && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return "GIF";
        }

        // BMP: 42 4D
        if (bytes.length >= 2 && bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return "BMP";
        }

        // WebP: 52 49 46 46 (RIFF) + WebP signature at offset 8
        if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 &&
            bytes[2] == 0x46 && bytes[3] == 0x46 &&
            bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "WebP (NO SOPORTADO)";
        }

        // RIFF sin WebP (podría ser WAV, AVI, etc.)
        if (bytes.length >= 4 && bytes[0] == 0x52 && bytes[1] == 0x49 &&
            bytes[2] == 0x46 && bytes[3] == 0x46) {
            return "RIFF (posiblemente WebP, WAV o AVI - NO SOPORTADO)";
        }

        return "desconocido (magic bytes: " +
            String.format("%02X %02X %02X %02X",
                bytes[0], bytes[1],
                bytes.length > 2 ? bytes[2] : 0,
                bytes.length > 3 ? bytes[3] : 0) + ")";
    }

    private String generarNombreArchivo(String nombreBase, String extension) {
        String nombreLimpio = nombreBase.toLowerCase()
                .replaceAll("[^a-z0-9áéíóúüñ]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");

        String nombreArchivo = nombreLimpio + extension;
        Path rutaCompleta = Paths.get(normalizarRuta(), nombreArchivo);

        int contador = 1;
        while (Files.exists(rutaCompleta)) {
            nombreArchivo = nombreLimpio + "_" + contador + extension;
            rutaCompleta = Paths.get(normalizarRuta(), nombreArchivo);
            contador++;
        }
        return nombreArchivo;
    }

    private BufferedImage redimensionarImagen(BufferedImage original) {
        int anchoOriginal = original.getWidth();
        int altoOriginal = original.getHeight();

        if (anchoOriginal <= MAX_WIDTH && altoOriginal <= MAX_HEIGHT) {
            return original;
        }

        double escala = Math.min((double) MAX_WIDTH / anchoOriginal, (double) MAX_HEIGHT / altoOriginal);
        int nuevoAncho = (int) (anchoOriginal * escala);
        int nuevoAlto = (int) (altoOriginal * escala);

        BufferedImage redimensionada = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = redimensionada.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
        g2d.dispose();

        logger.info("Imagen redimensionada: {}x{} -> {}x{}", anchoOriginal, altoOriginal, nuevoAncho, nuevoAlto);
        return redimensionada;
    }

    private byte[] comprimirImagen(BufferedImage imagen, String formato) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if ("jpg".equals(formato) || "jpeg".equals(formato)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);

                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(imagen, null, null), param);
                }
                writer.dispose();
            }
        } else {
            ImageIO.write(imagen, formato, baos);
        }
        return baos.toByteArray();
    }

    public String subirImagenPerfil(MultipartFile archivo, String nombreEmpleado) throws IOException {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }

        String nombreOriginal = archivo.getOriginalFilename();
        logger.info("Procesando imagen: {}, tamaño: {} bytes, tipo: {}",
            nombreOriginal, archivo.getSize(), archivo.getContentType());

        if (nombreOriginal == null || !esExtensionValida(nombreOriginal)) {
            throw new IllegalArgumentException("Formato de archivo no permitido. Use PNG, JPG, JPEG, GIF o BMP");
        }

        // Validar ContentType
        String contentType = archivo.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/octet-stream"))) {
            logger.warn("ContentType invalido: {}", contentType);
            throw new IllegalArgumentException("El archivo no es una imagen valida. ContentType: " + contentType);
        }

        if (archivo.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo es demasiado grande. Maximo 5MB");
        }

        if (archivo.getSize() == 0) {
            throw new IllegalArgumentException("El archivo tiene tamaño 0 bytes");
        }

        String extensionOriginal = nombreOriginal.substring(nombreOriginal.lastIndexOf(".") + 1).toLowerCase();
        String extensionFinal = extensionOriginal.equals("png") ? ".png" : ".jpg";
        String formatoSalida = extensionOriginal.equals("png") ? "png" : "jpg";

        // Leer bytes del archivo para evitar problemas con el stream
        byte[] bytesArchivo = archivo.getBytes();
        logger.info("Bytes leidos del archivo: {} bytes", bytesArchivo.length);

        // Detectar el formato real del archivo antes de intentar leerlo
        String formatoDetectado = detectarFormatoImagen(bytesArchivo);
        logger.info("Formato detectado por magic bytes: {}", formatoDetectado);

        // Rechazar archivos WebP o RIFF que no son soportados
        if (formatoDetectado.contains("WebP") || formatoDetectado.contains("RIFF")) {
            throw new IllegalArgumentException(
                "Formato WebP no soportado. " +
                "Por favor, convierta la imagen a PNG o JPG antes de subirla. " +
                "Puede usar herramientas online como convertio.co o cloudconvert.com"
            );
        }

        BufferedImage imagenOriginal = null;
        try {
            imagenOriginal = ImageIO.read(new ByteArrayInputStream(bytesArchivo));
        } catch (Exception e) {
            logger.error("Error al intentar leer imagen con ImageIO: {}", e.getMessage());
        }

        if (imagenOriginal == null) {
            logger.error("ImageIO no pudo leer la imagen. Nombre: {}, Tipo: {}, Tamaño: {} bytes, Extension: {}",
                nombreOriginal, archivo.getContentType(), bytesArchivo.length, extensionOriginal);

            throw new IllegalArgumentException(
                "No se pudo leer la imagen. Formato esperado: " + extensionOriginal +
                ", Formato detectado: " + formatoDetectado +
                ". Asegurese de que el archivo sea una imagen PNG, JPG, GIF o BMP valida."
            );
        }

        logger.info("Imagen leida exitosamente: {}x{} pixeles", imagenOriginal.getWidth(), imagenOriginal.getHeight());

        long tamanoOriginal = archivo.getSize();
        BufferedImage imagenRedimensionada = redimensionarImagen(imagenOriginal);
        byte[] imagenComprimida = comprimirImagen(imagenRedimensionada, formatoSalida);

        logger.info("Imagen optimizada: {} KB -> {} KB", tamanoOriginal / 1024, imagenComprimida.length / 1024);

        String nombreArchivo = generarNombreArchivo(nombreEmpleado, extensionFinal);
        String rutaDestino = normalizarRuta() + nombreArchivo;

        Path directorio = Paths.get(normalizarRuta());
        if (!Files.exists(directorio)) {
            Files.createDirectories(directorio);
        }

        Path ruta = Paths.get(rutaDestino);
        Files.write(ruta, imagenComprimida);

        return "img/" + nombreArchivo;
    }

    public void eliminarImagenAnterior(String nombreArchivo) throws IOException {
        if (nombreArchivo != null && !nombreArchivo.isEmpty() && !nombreArchivo.equals("img/perfil.png")) {
            String rutaCompleta = normalizarRuta() +
                nombreArchivo.replace("img/", "");
            Path ruta = Paths.get(rutaCompleta);
            if (Files.exists(ruta)) {
                Files.delete(ruta);
            }
        }
    }

    public boolean existeImagen(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.isEmpty()) {
            return false;
        }
        String rutaCompleta = normalizarRuta() +
            nombreArchivo.replace("img/", "");
        Path ruta = Paths.get(rutaCompleta);
        return Files.exists(ruta);
    }
}
