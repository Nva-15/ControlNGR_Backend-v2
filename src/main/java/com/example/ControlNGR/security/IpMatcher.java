package com.example.ControlNGR.security;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Compara una IP contra un patron de segmento. Formatos aceptados:
 * <ul>
 *   <li>Comodin: {@code 10.92.104.%} o {@code 10.92.104.*} (tambien {@code 10.92.%})</li>
 *   <li>CIDR: {@code 10.92.104.0/24}</li>
 *   <li>IP exacta: {@code 10.92.104.15}</li>
 * </ul>
 */
public final class IpMatcher {

    private static final Pattern OCTETO = Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private IpMatcher() {}

    public static boolean coincide(String ip, String patron) {
        if (ip == null || patron == null) return false;
        ip = normalizar(ip.trim());
        patron = patron.trim();
        if (!esIpv4(ip)) return false;

        if (patron.contains("/")) {
            return coincideCidr(ip, patron);
        }
        if (patron.contains("%") || patron.contains("*")) {
            String prefijo = patron.replace("*", "%");
            prefijo = prefijo.substring(0, prefijo.indexOf('%'));
            // "10.92.104.%" -> prefijo "10.92.104." ; se exige coincidencia por octetos completos
            return !prefijo.isEmpty() && prefijo.endsWith(".") && ip.startsWith(prefijo);
        }
        return ip.equals(patron);
    }

    /** Valida que un patron tenga uno de los formatos soportados. */
    public static boolean patronValido(String patron) {
        if (patron == null || patron.isBlank()) return false;
        patron = patron.trim();
        if (patron.contains("/")) {
            String[] partes = patron.split("/");
            if (partes.length != 2 || !esIpv4(partes[0])) return false;
            try {
                int bits = Integer.parseInt(partes[1]);
                return bits >= 0 && bits <= 32;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (patron.contains("%") || patron.contains("*")) {
            String p = patron.replace("*", "%");
            if (!p.endsWith(".%") || p.indexOf('%') != p.length() - 1) return false;
            String[] octetos = p.substring(0, p.length() - 2).split("\\.");
            if (octetos.length < 1 || octetos.length > 3) return false;
            for (String o : octetos) {
                if (!OCTETO.matcher(o).matches()) return false;
            }
            return true;
        }
        return esIpv4(patron);
    }

    private static boolean coincideCidr(String ip, String cidr) {
        try {
            String[] partes = cidr.split("/");
            int bits = Integer.parseInt(partes[1]);
            if (!esIpv4(partes[0]) || bits < 0 || bits > 32) return false;
            long mascara = bits == 0 ? 0 : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
            return (aLong(ip) & mascara) == (aLong(partes[0]) & mascara);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean esIpv4(String ip) {
        String[] octetos = ip.split("\\.", -1);
        if (octetos.length != 4) return false;
        for (String o : octetos) {
            if (!OCTETO.matcher(o).matches()) return false;
        }
        return true;
    }

    private static long aLong(String ip) throws Exception {
        byte[] b = InetAddress.getByName(ip).getAddress();
        return ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16) | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
    }

    /** Convierte IPv4 mapeada en IPv6 (::ffff:10.0.0.1) a IPv4. */
    private static String normalizar(String ip) {
        if (ip.startsWith("::ffff:")) return ip.substring(7);
        return ip;
    }
}
