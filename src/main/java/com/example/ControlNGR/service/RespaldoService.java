package com.example.ControlNGR.service;

import com.example.ControlNGR.entity.Respaldo;
import com.example.ControlNGR.entity.RespaldoProgramacion;
import com.example.ControlNGR.repository.RespaldoProgramacionRepository;
import com.example.ControlNGR.repository.RespaldoRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Respaldos de la base de datos (mysqldump) comprimidos en ZIP, opcionalmente con las fotos y
 * evidencias. Se generan a mano desde el panel admin o según la programación (diaria, semanal
 * o mensual a una hora). Solo se ejecuta uno a la vez y en un hilo aparte para no frenar el sistema.
 */
@Service
public class RespaldoService {

    private static final Logger log = LoggerFactory.getLogger(RespaldoService.class);
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final Set<String> FRECUENCIAS = Set.of("NINGUNA", "DIARIA", "SEMANAL", "MENSUAL");
    private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern URL_MYSQL = Pattern.compile("jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]+)");
    private static final long LIMITE_MINUTOS = 60;

    private final RespaldoRepository repository;
    private final RespaldoProgramacionRepository programacionRepository;
    private final Path carpeta;
    private final Path carpetaImagenes;
    private final Path carpetaEvidencias;
    private final String urlBd;
    private final String usuarioBd;
    private final String claveBd;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "respaldos");
        t.setDaemon(true);
        return t;
    });

    public RespaldoService(RespaldoRepository repository,
                           RespaldoProgramacionRepository programacionRepository,
                           @Value("${app.storage.respaldos:./data/respaldos}") String carpeta,
                           @Value("${app.storage.location:file:./data/img/}") String carpetaImagenes,
                           @Value("${app.storage.evidencias:./data/evidencias}") String carpetaEvidencias,
                           @Value("${spring.datasource.url}") String urlBd,
                           @Value("${spring.datasource.username}") String usuarioBd,
                           @Value("${spring.datasource.password:}") String claveBd) {
        this.repository = repository;
        this.programacionRepository = programacionRepository;
        this.carpeta = Paths.get(carpeta).toAbsolutePath().normalize();
        this.carpetaImagenes = Paths.get(carpetaImagenes.replaceFirst("^file:", "")).toAbsolutePath().normalize();
        this.carpetaEvidencias = Paths.get(carpetaEvidencias).toAbsolutePath().normalize();
        this.urlBd = urlBd;
        this.usuarioBd = usuarioBd;
        this.claveBd = claveBd;
    }

    /** Un respaldo que quedó "en curso" al apagarse el sistema no terminó: se marca fallido. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void alIniciar() {
        int n = repository.marcarInterrumpidos(ahora(), "Interrumpido: el sistema se detuvo durante el respaldo");
        if (n > 0) log.warn("{} respaldo(s) interrumpido(s) marcados como fallidos", n);
        try (Stream<Path> tmp = Files.list(carpeta)) {
            tmp.filter(p -> p.getFileName().toString().startsWith(".tmp_")).forEach(p -> p.toFile().delete());
        } catch (IOException ignorada) {
            // la carpeta aún no existe
        }
    }

    @PreDestroy
    public void detener() {
        ejecutor.shutdownNow();
    }

    // ---------- Consulta ----------

    @Transactional
    public Map<String, Object> estado() {
        // Se lee antes que la base: la transaccion ve una foto fija (REPEATABLE READ) y el respaldo
        // guarda su resultado antes de bajar la bandera. Asi nunca se informa "terminado" con un
        // registro que aun figura EN_CURSO.
        boolean respaldoEnCurso = enCurso.get();
        RespaldoProgramacion p = programacion();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("programacion", programacionMap(p));
        LocalDateTime proximo = proximaEjecucion(p, ahora());
        m.put("proximo", proximo);
        m.put("enCurso", respaldoEnCurso);
        m.put("carpetaDisponible", carpetaEscribible());
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Respaldo r : repository.findAllByOrderByIniciadoEnDescIdDesc()) lista.add(respaldoMap(r));
        m.put("respaldos", lista);
        return m;
    }

    @Transactional
    public Map<String, Object> actualizarProgramacion(Map<String, Object> body, String usuario) {
        String frecuencia = String.valueOf(body.getOrDefault("frecuencia", "")).trim().toUpperCase();
        if (!FRECUENCIAS.contains(frecuencia)) throw new IllegalArgumentException("Frecuencia no válida");
        LocalTime hora;
        try {
            hora = LocalTime.parse(String.valueOf(body.get("hora")).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Indique la hora en formato HH:mm");
        }
        int diaSemana = entero(body.get("diaSemana"), 1);
        int diaMes = entero(body.get("diaMes"), 1);
        int conservar = entero(body.get("conservar"), 10);
        if (diaSemana < 1 || diaSemana > 7) throw new IllegalArgumentException("Día de la semana no válido");
        if (diaMes < 1 || diaMes > 31) throw new IllegalArgumentException("El día del mes debe estar entre 1 y 31");
        if (conservar < 1 || conservar > 365) throw new IllegalArgumentException("Se deben conservar entre 1 y 365 respaldos");

        RespaldoProgramacion p = programacion();
        p.setFrecuencia(frecuencia);
        p.setHora(hora.withSecond(0).withNano(0));
        p.setDiaSemana(diaSemana);
        p.setDiaMes(diaMes);
        p.setConservar(conservar);
        p.setIncluirArchivos(!Boolean.FALSE.equals(body.get("incluirArchivos")));
        // Desde este momento: un horario ya pasado no dispara un respaldo inmediato
        p.setActualizadoEn(ahora());
        p.setActualizadoPor(usuario);
        programacionRepository.save(p);
        return programacionMap(p);
    }

    /** Archivo de un respaldo completado, validado contra la carpeta de respaldos. */
    @Transactional(readOnly = true)
    public Path archivo(Long id) {
        Respaldo r = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Respaldo no encontrado"));
        if (!"COMPLETADO".equals(r.getEstado())) throw new IllegalArgumentException("El respaldo no está completo");
        Path f = ruta(r.getArchivo());
        if (!Files.isRegularFile(f)) throw new IllegalArgumentException("El archivo ya no existe en la carpeta de respaldos");
        return f;
    }

    @Transactional
    public void eliminar(Long id) {
        Respaldo r = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Respaldo no encontrado"));
        if ("EN_CURSO".equals(r.getEstado())) throw new IllegalStateException("El respaldo aún está en curso");
        borrarArchivo(r);
        repository.delete(r);
    }

    // ---------- Ejecución ----------

    /** Respaldo manual: se genera en segundo plano; el panel consulta el estado. */
    public Map<String, Object> crearManual(boolean incluirArchivos, String usuario) {
        return respaldoMap(iniciar("MANUAL", incluirArchivos, usuario));
    }

    /** Revisa cada minuto si toca un respaldo programado (también recupera uno perdido con el sistema apagado). */
    @Scheduled(cron = "0 * * * * *", zone = "America/Lima")
    public void revisarProgramacion() {
        try {
            RespaldoProgramacion p = programacion();
            LocalDateTime ahora = ahora();
            LocalDateTime turno = ultimaEjecucion(p, ahora);
            if (turno == null || turno.isBefore(p.getActualizadoEn())) return;
            if (repository.existsByTipoAndIniciadoEnGreaterThanEqual("PROGRAMADO", turno)) return;
            if (enCurso.get()) return; // se reintenta el minuto siguiente
            log.info("Iniciando respaldo programado ({} {})", p.getFrecuencia(), turno);
            iniciar("PROGRAMADO", Boolean.TRUE.equals(p.getIncluirArchivos()), "Sistema");
        } catch (Exception e) {
            log.error("No se pudo revisar la programación de respaldos", e);
        }
    }

    private Respaldo iniciar(String tipo, boolean incluirArchivos, String usuario) {
        if (!enCurso.compareAndSet(false, true)) {
            throw new IllegalStateException("Ya hay un respaldo en curso. Espere a que termine.");
        }
        Respaldo r;
        try {
            LocalDateTime inicio = ahora();
            r = new Respaldo();
            r.setArchivo("controlngr_" + inicio.format(NOMBRE) + "_" + tipo.toLowerCase() + ".zip");
            r.setTipo(tipo);
            r.setEstado("EN_CURSO");
            r.setIncluyeArchivos(incluirArchivos);
            r.setIniciadoEn(inicio);
            r.setCreadoPor(usuario);
            r = repository.save(r);
        } catch (RuntimeException e) {
            enCurso.set(false);
            throw e;
        }
        final Long id = r.getId();
        ejecutor.submit(() -> ejecutar(id));
        return r;
    }

    private void ejecutar(Long id) {
        Respaldo r = repository.findById(id).orElse(null);
        if (r == null) { enCurso.set(false); return; }
        Path sql = carpeta.resolve(".tmp_" + r.getArchivo() + ".sql");
        Path zipTmp = carpeta.resolve(".tmp_" + r.getArchivo());
        try {
            Files.createDirectories(carpeta);
            if (!carpetaEscribible()) {
                throw new IOException("La carpeta de respaldos no tiene permiso de escritura");
            }
            volcarBaseDatos(sql);
            comprimir(sql, zipTmp, Boolean.TRUE.equals(r.getIncluyeArchivos()), r);
            Path destino = ruta(r.getArchivo());
            Files.move(zipTmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            r.setTamanoBytes(Files.size(destino));
            r.setEstado("COMPLETADO");
            r.setMensaje(null);
            log.info("Respaldo {} completado ({} bytes)", r.getArchivo(), r.getTamanoBytes());
        } catch (Exception e) {
            log.error("Respaldo {} fallido", r.getArchivo(), e);
            r.setEstado("FALLIDO");
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            r.setMensaje(msg.length() > 500 ? msg.substring(0, 500) : msg);
        } finally {
            quietoBorrar(sql);
            quietoBorrar(zipTmp);
            r.setFinalizadoEn(ahora());
            try {
                repository.save(r);
                if ("PROGRAMADO".equals(r.getTipo()) && "COMPLETADO".equals(r.getEstado())) aplicarRetencion();
            } catch (Exception e) {
                log.error("No se pudo registrar el resultado del respaldo", e);
            }
            enCurso.set(false);
        }
    }

    /**
     * mysqldump de toda la base, en el mismo formato que scripts/respaldar (con --databases, así
     * scripts/restaurar lo restaura). La tabla de respaldos va sin datos: su historial se refiere a
     * archivos de este equipo y no tiene sentido al restaurar en otro.
     */
    private void volcarBaseDatos(Path destino) throws IOException, InterruptedException {
        Matcher m = URL_MYSQL.matcher(urlBd);
        if (!m.find()) throw new IOException("No se reconoce la conexión a la base de datos");
        String host = m.group(1), puerto = m.group(2) == null ? "3306" : m.group(2), bd = m.group(3);
        List<String> base = List.of("mysqldump", "-h", host, "-P", puerto, "-u", usuarioBd,
                "--single-transaction", "--quick", "--routines", "--triggers", "--no-tablespaces",
                "--set-gtid-purged=OFF", "--default-character-set=utf8mb4");
        List<String> datos = new ArrayList<>(base);
        datos.add("--ignore-table=" + bd + ".respaldos");
        datos.add("--databases");
        datos.add(bd);
        List<String> estructura = new ArrayList<>(base);
        estructura.addAll(List.of("--no-data", bd, "respaldos"));
        Path errores = carpeta.resolve(".tmp_errores.txt");
        try {
            ejecutarProceso(datos, ProcessBuilder.Redirect.to(destino.toFile()), errores);
            ejecutarProceso(estructura, ProcessBuilder.Redirect.appendTo(destino.toFile()), errores);
        } finally {
            quietoBorrar(errores);
        }
    }

    private void ejecutarProceso(List<String> comando, ProcessBuilder.Redirect salida, Path errores)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando)
                .redirectOutput(salida)
                .redirectError(errores.toFile());
        pb.environment().put("MYSQL_PWD", claveBd == null ? "" : claveBd);
        Process proceso;
        try {
            proceso = pb.start();
        } catch (IOException e) {
            throw new IOException("mysqldump no está instalado en el servidor del backend", e);
        }
        if (!proceso.waitFor(LIMITE_MINUTOS, TimeUnit.MINUTES)) {
            proceso.destroyForcibly();
            throw new IOException("El respaldo superó el tiempo máximo de " + LIMITE_MINUTOS + " minutos");
        }
        if (proceso.exitValue() != 0) {
            String detalle = Files.exists(errores) ? Files.readString(errores, StandardCharsets.UTF_8).trim() : "";
            throw new IOException("mysqldump terminó con error " + proceso.exitValue()
                    + (detalle.isEmpty() ? "" : ": " + detalle));
        }
    }

    private void comprimir(Path sql, Path zip, boolean incluirArchivos, Respaldo r) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            agregar(out, sql, "controlngr.sql");
            if (incluirArchivos) {
                agregarCarpeta(out, carpetaImagenes, "img/");
                agregarCarpeta(out, carpetaEvidencias, "evidencias/");
            }
            out.putNextEntry(new ZipEntry("LEEME.txt"));
            out.write(leeme(r, incluirArchivos).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }

    private void agregarCarpeta(ZipOutputStream out, Path origen, String prefijo) throws IOException {
        if (!Files.isDirectory(origen)) return;
        List<Path> archivos;
        try (Stream<Path> s = Files.walk(origen)) {
            archivos = s.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path f : archivos) {
            String nombre = prefijo + origen.relativize(f).toString().replace('\\', '/');
            agregar(out, f, nombre);
        }
    }

    private static void agregar(ZipOutputStream out, Path archivo, String nombre) throws IOException {
        out.putNextEntry(new ZipEntry(nombre));
        try (InputStream in = Files.newInputStream(archivo)) {
            in.transferTo(out);
        }
        out.closeEntry();
    }

    private static String leeme(Respaldo r, boolean incluirArchivos) {
        return """
                Respaldo de Control NGR
                Generado: %s (%s)
                Contenido: controlngr.sql (base de datos)%s

                Para restaurarlo (en este equipo o en uno nuevo con el sistema instalado y el mismo .env),
                desde la carpeta ControlNGR_Backend-v2:

                  Windows:  powershell -ExecutionPolicy Bypass -File .\\scripts\\restaurar.ps1 -Respaldo RUTA\\%s
                  Linux:    sh scripts/restaurar.sh RUTA/%s

                ATENCIÓN: restaurar reemplaza todos los datos actuales por los de este respaldo.
                Las contraseñas de los usuarios van encriptadas: todos entran con la clave que ya tenían.
                Guarde este archivo en un lugar seguro: contiene datos personales.
                """.formatted(
                r.getIniciadoEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                "MANUAL".equals(r.getTipo()) ? "manual, " + r.getCreadoPor() : "programado",
                incluirArchivos ? ", img/ (fotos) y evidencias/ (sustentos de solicitudes)" : "",
                r.getArchivo(), r.getArchivo());
    }

    /** Conserva solo los últimos N respaldos programados; los manuales los elimina el admin. */
    @Transactional
    public void aplicarRetencion() {
        int conservar = Math.max(1, programacion().getConservar());
        List<Respaldo> programados = repository.findByTipoAndEstadoOrderByIniciadoEnDescIdDesc("PROGRAMADO", "COMPLETADO");
        for (Respaldo viejo : programados.subList(Math.min(conservar, programados.size()), programados.size())) {
            borrarArchivo(viejo);
            repository.delete(viejo);
            log.info("Respaldo antiguo eliminado: {}", viejo.getArchivo());
        }
    }

    // ---------- Programación ----------

    /** Último momento programado que ya llegó (o null si no hay programación). */
    static LocalDateTime ultimaEjecucion(RespaldoProgramacion p, LocalDateTime ahora) {
        LocalTime h = p.getHora();
        switch (p.getFrecuencia()) {
            case "DIARIA" -> {
                LocalDateTime hoy = ahora.toLocalDate().atTime(h);
                return hoy.isAfter(ahora) ? hoy.minusDays(1) : hoy;
            }
            case "SEMANAL" -> {
                DayOfWeek dia = DayOfWeek.of(p.getDiaSemana());
                LocalDateTime t = ahora.toLocalDate().with(TemporalAdjusters.previousOrSame(dia)).atTime(h);
                return t.isAfter(ahora) ? t.minusWeeks(1) : t;
            }
            case "MENSUAL" -> {
                LocalDateTime t = diaDelMes(YearMonth.from(ahora), p.getDiaMes()).atTime(h);
                return t.isAfter(ahora) ? diaDelMes(YearMonth.from(ahora).minusMonths(1), p.getDiaMes()).atTime(h) : t;
            }
            default -> { return null; }
        }
    }

    /** Próximo momento programado (para mostrarlo en el panel). */
    static LocalDateTime proximaEjecucion(RespaldoProgramacion p, LocalDateTime ahora) {
        LocalTime h = p.getHora();
        switch (p.getFrecuencia()) {
            case "DIARIA" -> {
                LocalDateTime hoy = ahora.toLocalDate().atTime(h);
                return hoy.isAfter(ahora) ? hoy : hoy.plusDays(1);
            }
            case "SEMANAL" -> {
                DayOfWeek dia = DayOfWeek.of(p.getDiaSemana());
                LocalDateTime t = ahora.toLocalDate().with(TemporalAdjusters.nextOrSame(dia)).atTime(h);
                return t.isAfter(ahora) ? t : t.plusWeeks(1);
            }
            case "MENSUAL" -> {
                LocalDateTime t = diaDelMes(YearMonth.from(ahora), p.getDiaMes()).atTime(h);
                return t.isAfter(ahora) ? t : diaDelMes(YearMonth.from(ahora).plusMonths(1), p.getDiaMes()).atTime(h);
            }
            default -> { return null; }
        }
    }

    private static LocalDate diaDelMes(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    // ---------- Utilidades ----------

    private RespaldoProgramacion programacion() {
        return programacionRepository.findById(RespaldoProgramacion.ID).orElseGet(() -> {
            RespaldoProgramacion p = new RespaldoProgramacion();
            p.setActualizadoEn(ahora());
            return programacionRepository.save(p);
        });
    }

    private boolean carpetaEscribible() {
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            return false;
        }
        return Files.isWritable(carpeta);
    }

    /** Solo nombres simples dentro de la carpeta de respaldos (nunca rutas). */
    private Path ruta(String archivo) {
        Path f = carpeta.resolve(archivo).normalize();
        if (!f.getParent().equals(carpeta)) throw new IllegalArgumentException("Nombre de archivo no válido");
        return f;
    }

    private void borrarArchivo(Respaldo r) {
        try {
            Files.deleteIfExists(ruta(r.getArchivo()));
        } catch (IOException e) {
            log.warn("No se pudo borrar {}", r.getArchivo(), e);
        }
    }

    private static void quietoBorrar(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignorada) {
            // archivo temporal
        }
    }

    private static int entero(Object v, int porDefecto) {
        if (v == null || String.valueOf(v).isBlank()) return porDefecto;
        try {
            return new java.math.BigDecimal(String.valueOf(v).trim()).intValueExact();
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor numérico no válido: " + v);
        }
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA).withNano(0);
    }

    private Map<String, Object> programacionMap(RespaldoProgramacion p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("frecuencia", p.getFrecuencia());
        m.put("hora", p.getHora().toString());
        m.put("diaSemana", p.getDiaSemana());
        m.put("diaMes", p.getDiaMes());
        m.put("conservar", p.getConservar());
        m.put("incluirArchivos", p.getIncluirArchivos());
        m.put("actualizadoEn", p.getActualizadoEn());
        m.put("actualizadoPor", p.getActualizadoPor());
        return m;
    }

    private Map<String, Object> respaldoMap(Respaldo r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("archivo", r.getArchivo());
        m.put("tipo", r.getTipo());
        m.put("estado", r.getEstado());
        m.put("incluyeArchivos", r.getIncluyeArchivos());
        m.put("tamanoBytes", r.getTamanoBytes());
        m.put("iniciadoEn", r.getIniciadoEn());
        m.put("finalizadoEn", r.getFinalizadoEn());
        m.put("mensaje", r.getMensaje());
        m.put("creadoPor", r.getCreadoPor());
        m.put("disponible", "COMPLETADO".equals(r.getEstado()) && Files.isRegularFile(carpeta.resolve(r.getArchivo()).normalize()));
        return m;
    }
}
