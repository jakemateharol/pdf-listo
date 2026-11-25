package pe.pdflisto.sistema;

import pe.pdflisto.operaciones.HAArchivos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class HADiagnostico {
    static final int HA_MAX_REGISTRO = 3 * 1024 * 1024;

    private static final String HA_EXTENSIONES =
            "pdf|docx?|odt|rtf|xlsx?|ods|csv|pptx?|txt|png|jpe?g|tiff?|bmp|gif|webp|heic|zip|rar|7z|xml|json|html?";
    private static final Pattern HA_RUTA_DOCUMENTO = Pattern.compile(
            "(?:[A-Za-z]:[\\\\/]|%USERPROFILE%[\\\\/]|\\\\\\\\)[^\\r\\n\"<>|]*?\\.(" + HA_EXTENSIONES + ")\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HA_NOMBRE_DOCUMENTO = Pattern.compile(
            "[^\\s\"'«»<>|/\\\\:*?()\\[\\]]+\\.(" + HA_EXTENSIONES + ")\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HA_OTRA_RUTA = Pattern.compile(
            "(?:[A-Za-z]:[\\\\/]|\\\\\\\\)[^\\r\\n\"'«»<>|,;]*");
    private static final Pattern HA_CORREO = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern HA_NUMERO_LARGO = Pattern.compile("(?<![\\d.])\\d{8,}(?![\\d])");
    private static final Pattern HA_CELULAR = Pattern.compile("(?<!\\d)9\\d{2}[ -]\\d{3}[ -]\\d{3}(?!\\d)");
    private static final List<String> HA_RUTAS_SEGURAS = List.of(
            "%USERPROFILE%\\AppData", "%USERPROFILE%/AppData", "C:\\Program Files", "C:\\Windows", "C:/Program Files",
            "C:/Windows", "%USERPROFILE%\\.m2", "%USERPROFILE%/.m2", "%USERPROFILE%\\.jdks", "%USERPROFILE%/.jdks");

    private final String haUsuario;
    private final String haCarpetaUsuario;

    public HADiagnostico() {
        this(System.getProperty("user.name"), System.getProperty("user.home"));
    }

    HADiagnostico(String haUsuario, String haCarpetaUsuario) {
        this.haUsuario = haUsuario == null ? "" : haUsuario;
        this.haCarpetaUsuario = haCarpetaUsuario == null ? "" : haCarpetaUsuario;
    }

    public record HAResumen(Path haArchivo, long haBytes, int haLineasRegistro) {
    }

    public HAResumen haCrear(Path haDestino, String haVersion, List<HAEstadoComponente> haComponentes, Path haCarpetaLogs,
                         Path haCarpetaDatos) throws IOException {
        List<String> haRegistro = haRegistro(haCarpetaLogs);
        Path haTemporal = Files.createTempFile(haDestino.toAbsolutePath().getParent(), ".pdflisto-", ".tmp");
        try {
            try (OutputStream haOut = Files.newOutputStream(haTemporal); ZipOutputStream haZip = new ZipOutputStream(haOut)) {
                haAgregar(haZip, "LEEME.txt", haLeeme());
                haAgregar(haZip, "sistema.txt", haSistema(haVersion, haComponentes, haCarpetaDatos));
                haAgregar(haZip, "registro.txt", haRegistro.isEmpty() ? "(el registro está vacío)\n" : String.join("\n", haRegistro) + "\n");
            }
            Files.move(haTemporal, haDestino, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(haTemporal);
        }
        return new HAResumen(haDestino, Files.size(haDestino), haRegistro.size());
    }

    public String haLimpiar(String haTexto) {
        if (haTexto == null || haTexto.isEmpty()) {
            return haTexto;
        }
        String haT = haTexto;
        if (haCarpetaUsuario.length() > 3) {
            haT = haReemplazarSinMayusculas(haT, haCarpetaUsuario, "%USERPROFILE%");
            haT = haReemplazarSinMayusculas(haT, haCarpetaUsuario.replace('\\', '/'), "%USERPROFILE%");
        }
        haT = haReemplazar(haT, HA_RUTA_DOCUMENTO, haM -> "[archivo ." + haM.group(1).toLowerCase(Locale.ROOT) + "]");
        haT = haReemplazar(haT, HA_OTRA_RUTA, haM -> haEsSegura(haM.group()) ? haM.group() : "[carpeta]");
        haT = haReemplazar(haT, Pattern.compile("%USERPROFILE%[\\\\/][^\\r\\n\"'«»<>|,;]*"),
                haM -> haEsSegura(haM.group()) ? haM.group() : "[carpeta]");
        haT = haReemplazar(haT, HA_NOMBRE_DOCUMENTO, haM -> "[archivo ." + haM.group(1).toLowerCase(Locale.ROOT) + "]");
        haT = haReemplazar(haT, HA_CORREO, haM -> "[correo]");
        haT = haReemplazar(haT, HA_CELULAR, haM -> "[número]");
        haT = haReemplazar(haT, HA_NUMERO_LARGO, haM -> "[número]");
        if (haUsuario.length() >= 3) {
            haT = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(haUsuario) + "(?![\\p{L}\\d])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(haT).replaceAll("[usuario]");
        }
        return haT;
    }

    private static boolean haEsSegura(String haRuta) {
        String haR = haRuta.toLowerCase(Locale.ROOT);
        return HA_RUTAS_SEGURAS.stream().anyMatch(haS -> haR.startsWith(haS.toLowerCase(Locale.ROOT)));
    }

    private static String haReemplazar(String haTexto, Pattern haPatron, java.util.function.Function<Matcher, String> haPor) {
        Matcher haM = haPatron.matcher(haTexto);
        StringBuilder haSb = new StringBuilder();
        while (haM.find()) {
            haM.appendReplacement(haSb, Matcher.quoteReplacement(haPor.apply(haM)));
        }
        haM.appendTail(haSb);
        return haSb.toString();
    }

    private static String haReemplazarSinMayusculas(String haTexto, String haBuscado, String haPor) {
        return Pattern.compile(Pattern.quote(haBuscado), Pattern.CASE_INSENSITIVE).matcher(haTexto)
                .replaceAll(Matcher.quoteReplacement(haPor));
    }

    private String haLeeme() {
        return """
                DIAGNÓSTICO DE PDF LISTO
                ========================
                Este archivo sirve para que soporte técnico entienda un problema de la aplicación.

                Contiene:
                  - sistema.txt   versión de PDF Listo, Java, Windows, memoria, espacio libre y estado de
                                  los componentes (OCR, Visual C++, LibreOffice…).
                  - registro.txt  el registro de errores de la aplicación (lo más reciente).

                NO contiene:
                  - ningún PDF ni otro documento, ni su contenido;
                  - el historial, los archivos recientes, las recetas ni los perfiles (base de datos);
                  - la configuración (por ejemplo la última carpeta usada), las firmas ni la carpeta de trabajo.

                Además, antes de guardarlo se reemplazaron por marcas:
                  - rutas y nombres de archivos          → [archivo .pdf], [carpeta]
                  - el nombre de usuario de Windows       → [usuario], %USERPROFILE%
                  - correos y números largos (DNI, RUC…)  → [correo], [número]

                Puedes abrir los .txt con el Bloc de notas y revisar todo antes de enviarlo.
                Se creó sin internet; PDF Listo no lo envía a ningún lado.
                """;
    }

    private String haSistema(String haVersion, List<HAEstadoComponente> haComponentes, Path haCarpetaDatos) {
        Runtime haRt = Runtime.getRuntime();
        StringBuilder haSb = new StringBuilder();
        haSb.append("PDF Listo ").append(haVersion).append('\n');
        haSb.append("Creado: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n\n");
        haSb.append("Windows: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(" (").append(System.getProperty("os.arch")).append(")\n");
        haSb.append("Java: ").append(System.getProperty("java.version")).append(" — ").append(System.getProperty("java.vendor"))
                .append('\n');
        haSb.append("JavaFX: ").append(System.getProperty("javafx.runtime.version", "?")).append('\n');
        haSb.append("Procesadores: ").append(haRt.availableProcessors()).append('\n');
        haSb.append("Memoria: máxima ").append(HAArchivos.haTamanoLegible(haRt.maxMemory())).append(", en uso ")
                .append(HAArchivos.haTamanoLegible(haRt.totalMemory() - haRt.freeMemory())).append('\n');
        haSb.append("Idioma: ").append(Locale.getDefault()).append('\n');
        if (haCarpetaDatos != null) {
            haSb.append("Espacio libre en el disco de datos: ").append(HAArchivos.haTamanoLegible(HAEspacioDisco.haLibre(haCarpetaDatos)))
                    .append('\n');
        }
        haSb.append("\nCOMPONENTES\n-----------\n");
        if (haComponentes == null || haComponentes.isEmpty()) {
            haSb.append("(no se pudo comprobar)\n");
        } else {
            for (HAEstadoComponente haE : haComponentes) {
                haSb.append(haE.haNivel()).append(" | ").append(haE.haNombre()).append(" | ").append(haE.haDetalle()).append('\n');
            }
        }
        return haLimpiar(haSb.toString());
    }

    List<String> haRegistro(Path haCarpetaLogs) throws IOException {
        List<Path> haArchivos = new ArrayList<>();
        if (haCarpetaLogs != null && Files.isDirectory(haCarpetaLogs)) {
            try (DirectoryStream<Path> haDs = Files.newDirectoryStream(haCarpetaLogs, "pdflisto.log*")) {
                haDs.forEach(haArchivos::add);
            }
        }
        haArchivos.sort(Comparator.comparing((Path haP) -> !haP.getFileName().toString().equals("pdflisto.log"))
                .thenComparing(HADiagnostico::haModificado, Comparator.reverseOrder()));
        Deque<String> haLineas = new ArrayDeque<>();
        long haTamano = 0;
        for (Path haArchivo : haArchivos) {
            List<String> haDelArchivo = haLeer(haArchivo);
            for (int haI = haDelArchivo.size() - 1; haI >= 0 && haTamano < HA_MAX_REGISTRO; haI--) {
                String haLimpia = haLimpiar(haDelArchivo.get(haI));
                haLineas.addFirst(haLimpia);
                haTamano += haLimpia.length() + 1;
            }
            if (haTamano >= HA_MAX_REGISTRO) {
                haLineas.addFirst("(… se omitieron líneas más antiguas del registro …)");
                break;
            }
        }
        return new ArrayList<>(haLineas);
    }

    private static long haModificado(Path haP) {
        try {
            return Files.getLastModifiedTime(haP).toMillis();
        } catch (IOException haE) {
            return 0;
        }
    }

    private static List<String> haLeer(Path haArchivo) {
        try (InputStream haBase = Files.newInputStream(haArchivo);
             InputStream haIn = haArchivo.toString().endsWith(".gz") ? new GZIPInputStream(haBase) : haBase;
             BufferedReader haR = new BufferedReader(new InputStreamReader(haIn, StandardCharsets.UTF_8))) {
            return haR.lines().toList();
        } catch (IOException | java.io.UncheckedIOException haE) {
            return List.of("(no se pudo leer un archivo del registro: " + haE.getClass().getSimpleName() + ")");
        }
    }

    private static void haAgregar(ZipOutputStream haZip, String haNombre, String haContenido) throws IOException {
        haZip.putNextEntry(new ZipEntry(haNombre));
        haZip.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        haZip.write(haContenido.replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
        haZip.closeEntry();
    }
}
