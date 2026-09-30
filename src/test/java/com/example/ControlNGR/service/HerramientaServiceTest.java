package com.example.ControlNGR.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class HerramientaServiceTest {

    private static String imagen(int ancho, int alto, String formato) throws Exception {
        BufferedImage img = new BufferedImage(ancho, alto, formato.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < ancho; x++) for (int y = 0; y < alto; y++) img.setRGB(x, y, (x * 7 + y) | 0xFF000000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        return "data:image/" + formato + ";base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static BufferedImage leer(String base64) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    @Test
    void enlacesValidos() {
        assertEquals("https://nagios.ngr.local/nagios", HerramientaService.validarUrl("https://nagios.ngr.local/nagios"));
        assertEquals("http://10.92.104.20:8080/glpi/", HerramientaService.validarUrl("http://10.92.104.20:8080/glpi/"));
    }

    @Test
    void enlacesPeligrososORotos() {
        for (String u : new String[]{"javascript:alert(1)", "JAVASCRIPT:alert(1)", "data:text/html,<script>", "ftp://x",
                "nagios.local", "https://", "http:// espacio.com", "https://a.com/\"onmouseover=x", ""}) {
            assertThrows(IllegalArgumentException.class, () -> HerramientaService.validarUrl(u), u);
        }
    }

    @Test
    void logoGrandeSeReduceA128() throws Exception {
        BufferedImage r = leer(HerramientaService.procesarLogo(imagen(800, 400, "jpg")));
        assertEquals(128, r.getWidth());
        assertEquals(64, r.getHeight());
    }

    @Test
    void logoPequenoNoSeAgranda() throws Exception {
        BufferedImage r = leer(HerramientaService.procesarLogo(imagen(40, 40, "png")));
        assertEquals(40, r.getWidth());
    }

    @Test
    void siempreSeGuardaComoPng() throws Exception {
        String r = HerramientaService.procesarLogo(imagen(50, 50, "gif"));
        byte[] b = Base64.getDecoder().decode(r);
        assertEquals((byte) 0x89, b[0]);
        assertEquals('P', b[1]);
    }

    @Test
    void rechazaLoQueNoEsImagen() {
        String svg = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(
                "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8));
        String html = Base64.getEncoder().encodeToString("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> HerramientaService.procesarLogo(svg));
        assertThrows(IllegalArgumentException.class, () -> HerramientaService.procesarLogo(html));
        assertThrows(IllegalArgumentException.class, () -> HerramientaService.procesarLogo("esto no es base64 %%%"));
    }

    @Test
    void rechazaImagenesDeMasDe2MB() {
        byte[] grande = new byte[2 * 1024 * 1024 + 10];
        grande[0] = (byte) 0x89;
        assertThrows(IllegalArgumentException.class,
                () -> HerramientaService.procesarLogo(Base64.getEncoder().encodeToString(grande)));
    }
}
