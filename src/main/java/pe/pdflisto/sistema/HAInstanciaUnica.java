package pe.pdflisto.sistema;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public final class HAInstanciaUnica implements AutoCloseable {
    static final String HA_BLOQUEO = "instancia.lock";
    static final String HA_PUERTO = "instancia.puerto";
    private static final String HA_SALUDO = "PDFLISTO 1";
    private static final int HA_MAX_ARCHIVOS = 500;

    private static volatile HAInstanciaUnica haActual;

    private final FileChannel haCanal;
    private final FileLock haBloqueo;
    private final ServerSocket haBuzon;
    private final String haClave;
    private final Path haCarpeta;
    private final List<List<File>> haPendientes = new ArrayList<>();
    private Consumer<List<File>> haReceptor;

    private HAInstanciaUnica(Path haCarpeta, FileChannel haCanal, FileLock haBloqueo, ServerSocket haBuzon, String haClave) {
        this.haCarpeta = haCarpeta;
        this.haCanal = haCanal;
        this.haBloqueo = haBloqueo;
        this.haBuzon = haBuzon;
        this.haClave = haClave;
    }

    public static HAInstanciaUnica haIniciar(Path haCarpeta, List<File> haArchivos, long haEspera) {
        long haLimite = System.currentTimeMillis() + haEspera;
        while (true) {
            try {
                HAInstanciaUnica haPropia = haTomar(haCarpeta);
                if (haPropia != null) {
                    haActual = haPropia;
                    return haPropia;
                }
                if (haEntregar(haCarpeta, haArchivos)) {
                    log.info("PDF Listo ya estaba abierto: se le enviaron {} archivo(s)", haArchivos.size());
                    return null;
                }
            } catch (IOException haE) {
                log.warn("Instancia única: {}", haE.getMessage());
            }
            if (System.currentTimeMillis() >= haLimite) {
                log.warn("Otra ventana de PDF Listo no responde; se abre una nueva");
                HAInstanciaUnica haSinBuzon = new HAInstanciaUnica(haCarpeta, null, null, null, null);
                haActual = haSinBuzon;
                return haSinBuzon;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException haE) {
                Thread.currentThread().interrupt();
                return new HAInstanciaUnica(haCarpeta, null, null, null, null);
            }
        }
    }

    public static HAInstanciaUnica haActual() {
        return haActual;
    }

    public void haAlRecibir(Consumer<List<File>> haReceptor) {
        List<List<File>> haAtrasados;
        synchronized (this) {
            this.haReceptor = haReceptor;
            haAtrasados = new ArrayList<>(haPendientes);
            haPendientes.clear();
        }
        haAtrasados.forEach(haReceptor);
    }

    public boolean haEsPrincipal() {
        return haBuzon != null;
    }

    private static HAInstanciaUnica haTomar(Path haCarpeta) throws IOException {
        Files.createDirectories(haCarpeta);
        FileChannel haCanal = FileChannel.open(haCarpeta.resolve(HA_BLOQUEO), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock haBloqueo;
        try {
            haBloqueo = haCanal.tryLock();
        } catch (OverlappingFileLockException haE) {
            haBloqueo = null;
        }
        if (haBloqueo == null) {
            haCanal.close();
            return null;
        }
        ServerSocket haBuzon = new ServerSocket();
        try {
            haBuzon.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
            byte[] haAzar = new byte[24];
            new SecureRandom().nextBytes(haAzar);
            String haClave = HexFormat.of().formatHex(haAzar);
            Path haTemporal = haCarpeta.resolve(HA_PUERTO + ".tmp");
            Files.writeString(haTemporal, haBuzon.getLocalPort() + "\n" + haClave + "\n", StandardCharsets.UTF_8);
            Files.move(haTemporal, haCarpeta.resolve(HA_PUERTO), StandardCopyOption.REPLACE_EXISTING);
            HAInstanciaUnica haPropia = new HAInstanciaUnica(haCarpeta, haCanal, haBloqueo, haBuzon, haClave);
            Thread haHilo = new Thread(haPropia::haAtender, "pdflisto-instancia-unica");
            haHilo.setDaemon(true);
            haHilo.start();
            return haPropia;
        } catch (IOException | RuntimeException haE) {
            haBuzon.close();
            haBloqueo.release();
            haCanal.close();
            throw haE;
        }
    }

    private void haAtender() {
        while (!haBuzon.isClosed()) {
            try (Socket haCliente = haBuzon.accept()) {
                haCliente.setSoTimeout(3000);
                List<File> haArchivos = haLeerPedido(haCliente);
                if (haArchivos != null) {
                    haEntregarAlReceptor(haArchivos);
                }
            } catch (IOException haE) {
                if (!haBuzon.isClosed()) {
                    log.debug("Instancia única: conexión descartada ({})", haE.getMessage());
                }
            }
        }
    }

    private List<File> haLeerPedido(Socket haCliente) throws IOException {
        BufferedReader haIn = new BufferedReader(new InputStreamReader(haCliente.getInputStream(), StandardCharsets.UTF_8));
        if (!HA_SALUDO.equals(haIn.readLine()) || !haClave.equals(haIn.readLine())) {
            log.warn("Instancia única: se rechazó una conexión sin la clave correcta");
            return null;
        }
        int haCantidad = Integer.parseInt(haIn.readLine().trim());
        if (haCantidad < 0 || haCantidad > HA_MAX_ARCHIVOS) {
            return null;
        }
        List<File> haArchivos = new ArrayList<>();
        for (int haI = 0; haI < haCantidad; haI++) {
            String haRuta = haIn.readLine();
            if (haRuta == null) {
                return null;
            }
            haArchivos.add(new File(haRuta));
        }
        Writer haOut = new OutputStreamWriter(haCliente.getOutputStream(), StandardCharsets.UTF_8);
        haOut.write("OK\n");
        haOut.flush();
        return haArchivos;
    }

    private void haEntregarAlReceptor(List<File> haArchivos) {
        Consumer<List<File>> haR;
        synchronized (this) {
            haR = haReceptor;
            if (haR == null) {
                haPendientes.add(haArchivos);
                return;
            }
        }
        haR.accept(haArchivos);
    }

    private static boolean haEntregar(Path haCarpeta, List<File> haArchivos) {
        List<String> haDatos;
        try {
            haDatos = Files.readAllLines(haCarpeta.resolve(HA_PUERTO), StandardCharsets.UTF_8);
        } catch (IOException haE) {
            return false;
        }
        if (haDatos.size() < 2) {
            return false;
        }
        try (Socket haS = new Socket()) {
            haS.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(haDatos.get(0).trim())), 1000);
            haS.setSoTimeout(3000);
            Writer haOut = new OutputStreamWriter(haS.getOutputStream(), StandardCharsets.UTF_8);
            haOut.write(HA_SALUDO + "\n" + haDatos.get(1).trim() + "\n" + haArchivos.size() + "\n");
            for (File haF : haArchivos) {
                haOut.write(haF.getAbsolutePath().replace("\n", "") + "\n");
            }
            haOut.flush();
            BufferedReader haIn = new BufferedReader(new InputStreamReader(haS.getInputStream(), StandardCharsets.UTF_8));
            return "OK".equals(haIn.readLine());
        } catch (IOException | NumberFormatException haE) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            if (haBuzon != null) {
                haBuzon.close();
                Files.deleteIfExists(haCarpeta.resolve(HA_PUERTO));
            }
            if (haBloqueo != null) {
                haBloqueo.release();
            }
            if (haCanal != null) {
                haCanal.close();
            }
        } catch (IOException haE) {
            log.debug("Instancia única: al cerrar ({})", haE.getMessage());
        }
        if (haActual == this) {
            haActual = null;
        }
    }
}
