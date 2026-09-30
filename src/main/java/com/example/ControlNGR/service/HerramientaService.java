package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Herramienta;
import com.example.ControlNGR.entity.TipoUsuario;
import com.example.ControlNGR.entity.Usuario;
import com.example.ControlNGR.repository.HerramientaRepository;
import com.example.ControlNGR.repository.TipoUsuarioRepository;
import com.example.ControlNGR.security.AccesoDenegadoException;
import com.example.ControlNGR.security.Roles;
import com.example.ControlNGR.security.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Vista "+ Herramientas": enlaces a las herramientas web del equipo (Nagios, GLPI, etc.).
 * Todo el personal ve las herramientas de su rol; la gestion (jefaturas, supervisores, gestor)
 * y el admin las ven todas y las crean, editan o eliminan.
 */
@Service
public class HerramientaService {

    private static final int LOGO_LADO = 128;
    private static final int LOGO_MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern URL_WEB = Pattern.compile("^https?://[^\\s<>\"']+$", Pattern.CASE_INSENSITIVE);

    private final HerramientaRepository repository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final UsuarioActual usuarioActual;

    public HerramientaService(HerramientaRepository repository, TipoUsuarioRepository tipoUsuarioRepository,
                              UsuarioActual usuarioActual) {
        this.repository = repository;
        this.tipoUsuarioRepository = tipoUsuarioRepository;
        this.usuarioActual = usuarioActual;
    }

    public static boolean puedeGestionar(String rol) {
        return Roles.esAdmin(rol) || Roles.esGestion(rol);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar() {
        Usuario usuario = usuarioActual.requerido();
        String rol = usuario.getRol() == null ? "" : usuario.getRol().toLowerCase();
        boolean gestiona = puedeGestionar(rol);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Herramienta h : repository.findAllByOrderByTituloAsc()) {
            boolean visible = Boolean.TRUE.equals(h.getTodosLosRoles()) || h.getRoles().contains(rol);
            if (gestiona || visible) lista.add(mapa(h, gestiona));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("puedeGestionar", gestiona);
        m.put("herramientas", lista);
        return m;
    }

    @Transactional
    public Map<String, Object> crear(Map<String, Object> body) {
        Usuario usuario = exigirGestion();
        Herramienta h = new Herramienta();
        h.setCreadoPor(usuario.getUsername());
        aplicar(h, body, true);
        return mapa(repository.save(h), true);
    }

    @Transactional
    public Map<String, Object> actualizar(Integer id, Map<String, Object> body) {
        exigirGestion();
        Herramienta h = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Herramienta no encontrada"));
        aplicar(h, body, false);
        h.setActualizadoEn(LocalDateTime.now());
        return mapa(repository.save(h), true);
    }

    @Transactional
    public void eliminar(Integer id) {
        exigirGestion();
        Herramienta h = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Herramienta no encontrada"));
        repository.delete(h);
    }

    // ---------- Validacion ----------

    private Usuario exigirGestion() {
        Usuario usuario = usuarioActual.requerido();
        if (!puedeGestionar(usuario.getRol())) {
            throw new AccesoDenegadoException("Solo jefaturas, supervisores y el administrador gestionan las herramientas");
        }
        return usuario;
    }

    private void aplicar(Herramienta h, Map<String, Object> body, boolean nueva) {
        String titulo = texto(body.get("titulo"));
        if (titulo.isEmpty()) throw new IllegalArgumentException("El título es obligatorio");
        if (titulo.length() > 100) throw new IllegalArgumentException("El título admite hasta 100 caracteres");
        String descripcion = texto(body.get("descripcion"));
        if (descripcion.length() > 500) throw new IllegalArgumentException("La descripción admite hasta 500 caracteres");
        h.setTitulo(titulo);
        h.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        h.setUrl(validarUrl(texto(body.get("url"))));

        boolean todos = !Boolean.FALSE.equals(body.get("todosLosRoles"));
        Set<String> roles = new LinkedHashSet<>();
        if (!todos) {
            Object lista = body.get("roles");
            if (lista instanceof Collection<?> c) {
                for (Object o : c) roles.add(validarRol(String.valueOf(o)));
            }
            if (roles.isEmpty()) throw new IllegalArgumentException("Seleccione al menos un rol o marque 'Todos los roles'");
        }
        h.setTodosLosRoles(todos);
        h.getRoles().clear();
        h.getRoles().addAll(roles);

        // logo: ausente = sin cambios (al editar); null o "" = quitar; data URL = reemplazar
        if (body.containsKey("logo")) {
            Object logo = body.get("logo");
            h.setLogo(logo == null || String.valueOf(logo).isBlank() ? null : procesarLogo(String.valueOf(logo)));
        } else if (nueva) {
            h.setLogo(null);
        }
    }

    static String validarUrl(String url) {
        if (url.isEmpty()) throw new IllegalArgumentException("El enlace es obligatorio");
        if (url.length() > 500) throw new IllegalArgumentException("El enlace admite hasta 500 caracteres");
        if (!URL_WEB.matcher(url).matches()) {
            throw new IllegalArgumentException("El enlace debe empezar con http:// o https://");
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El enlace no es una dirección web válida");
        }
        return url;
    }

    private String validarRol(String codigo) {
        String c = codigo == null ? "" : codigo.trim().toLowerCase();
        TipoUsuario t = tipoUsuarioRepository.findByCodigo(c)
                .orElseThrow(() -> new IllegalArgumentException("Rol no válido: " + codigo));
        if (Boolean.TRUE.equals(t.getEsSistema())) throw new IllegalArgumentException("Rol no válido: " + codigo);
        return t.getCodigo();
    }

    /**
     * Acepta una imagen PNG, JPG o GIF (data URL o base64) y la guarda como PNG de hasta 128x128.
     * Al volver a dibujarla se descarta cualquier contenido que no sea la imagen.
     */
    static String procesarLogo(String entrada) {
        String base64 = entrada.trim();
        int coma = base64.indexOf(',');
        if (base64.startsWith("data:") && coma > 0) base64 = base64.substring(coma + 1);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La imagen no es válida");
        }
        if (bytes.length > LOGO_MAX_BYTES) throw new IllegalArgumentException("La imagen supera los 2 MB");
        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            original = null;
        }
        if (original == null || original.getWidth() < 1 || original.getHeight() < 1) {
            throw new IllegalArgumentException("La imagen debe ser PNG, JPG o GIF");
        }
        double escala = Math.min(1.0, (double) LOGO_LADO / Math.max(original.getWidth(), original.getHeight()));
        int ancho = Math.max(1, (int) Math.round(original.getWidth() * escala));
        int alto = Math.max(1, (int) Math.round(original.getHeight() * escala));
        BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, ancho, alto, null);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(destino, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo procesar la imagen");
        }
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private Map<String, Object> mapa(Herramienta h, boolean conGestion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("titulo", h.getTitulo());
        m.put("descripcion", h.getDescripcion());
        m.put("url", h.getUrl());
        m.put("logo", h.getLogo() == null ? null : "data:image/png;base64," + h.getLogo());
        m.put("todosLosRoles", h.getTodosLosRoles());
        if (conGestion) {
            m.put("roles", new ArrayList<>(h.getRoles()));
            m.put("creadoPor", h.getCreadoPor());
        }
        return m;
    }
}
