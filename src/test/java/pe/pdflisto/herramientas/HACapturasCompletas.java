package pe.pdflisto.herramientas;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pe.pdflisto.HAPdfListoApplication;
import pe.pdflisto.anotaciones.HAAnotacion;
import pe.pdflisto.anotaciones.HAAnotar;
import pe.pdflisto.controller.HAMainController;
import pe.pdflisto.conversiones.HAConversor;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HADivisor;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAPlanDivision;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.privacidad.HAProteger;
import pe.pdflisto.ui.HADialogos;
import pe.pdflisto.ui.HANotificador;
import pe.pdflisto.verificacion.HAEsperadoArchivos;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class HACapturasCompletas extends Application {
    private static Path haSalida;
    private static Path haEjemplos;
    private static Path haDatos;
    private static Path haTrabajo;

    private ConfigurableApplicationContext haContexto;
    private Scene haEscena;
    private Stage haStage;
    private HAMainController haControlador;
    private volatile int haPestanaDialogo;
    private volatile String haNombreDialogo;

    public static void main(String[] haArgs) throws Exception {
        haSalida = Path.of(haArgs[0]);
        haDatos = Path.of(haArgs[1]);
        haEjemplos = Path.of(haArgs[2]);
        Files.createDirectories(haSalida);
        Files.createDirectories(haDatos.resolve("logs"));
        haTrabajo = Files.createDirectories(haDatos.resolve("auxiliares"));
        System.setProperty("app.datos", haDatos.toAbsolutePath().toString().replace('\\', '/'));
        haPrepararRecuperacion();
        launch(HACapturasCompletas.class, haArgs);
    }

    private static void haPrepararRecuperacion() throws Exception {
        File haOriginal = haEjemplos.resolve("demo-12-paginas.pdf").toFile();
        Path haCarpeta = haDatos.resolve("trabajo").resolve("sesion-recuperar");
        Constructor<HASesionEdicion> haC = HASesionEdicion.class.getDeclaredConstructor(File.class, String.class, Path.class);
        haC.setAccessible(true);
        HASesionEdicion haS = haC.newInstance(haOriginal, null, haCarpeta);
        File haV = haS.haNuevoArchivo();
        new HAMotorPdf().haEjecutar(haOriginal, null, new pe.pdflisto.operaciones.HARotar(List.of(0), 90), haV);
        haS.haAgregar(new HASesionEdicion.HAVersion(haV, "Rotar página 1 a la derecha"));
        haS.haLiberarSinBorrar();
    }

    @Override
    public void init() {
        haContexto = new SpringApplicationBuilder(HAPdfListoApplication.class).web(WebApplicationType.NONE).run();
    }

    @Override
    public void start(Stage haStage) throws Exception {
        this.haStage = haStage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        FXMLLoader haLoader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        haLoader.setControllerFactory(haContexto::getBean);
        haEscena = new Scene(haLoader.load(), 1680, 960);
        haEscena.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        haStage.setScene(haEscena);
        haControlador = haLoader.getController();
        HADialogos.haCapturador = this::haFotoDialogo;
        haControlador.haAlMostrarVentana(haStage);
        Thread haGuion = new Thread(this::haGuion, "capturas");
        haGuion.setDaemon(true);
        haGuion.start();
    }

    private void haGuion() {
        try {
            haEsperar(3000);
            haFoto("c00-calentar");
            haFoto("m001-recuperar-pregunta");
            haPulsar("Recuperar");
            haEsperar(4000);
            haQuitarAvisos();
            haFoto("m002-recuperado");
            haCerrarTodo();

            haFx(() -> haContexto.getBean(pe.pdflisto.controller.HABienvenida.class).haMostrar());
            haEsperar(800);
            haFoto("m003-bienvenida-1");
            haPulsar("Siguiente");
            haEsperar(600);
            haFoto("m004-bienvenida-2");
            haPulsar("Saltar");
            haEsperar(600);

            haAbrir("demo-formulario.pdf");
            haAbrir("demo-datos-sensibles.pdf");
            haCerrarTodo();
            haEsperar(800);
            haQuitarAvisos();
            haFoto("m005-inicio-recientes");
            haFx(() -> {
                Node haCapa = haEscena.lookup("#haCapaArrastre");
                haCapa.setVisible(true);
                haCapa.setManaged(true);
            });
            haEsperar(400);
            haFoto("m006-arrastrar");
            haFx(() -> {
                Node haCapa = haEscena.lookup("#haCapaArrastre");
                haCapa.setVisible(false);
            });

            haAbrir("demo-12-paginas.pdf");
            haFoto("m007-documento-abierto");

            File haProtegido = haTrabajo.resolve("contrato-protegido.pdf").toFile();
            new HAMotorPdf().haEjecutar(haEjemplos.resolve("demo-formulario.pdf").toFile(), null,
                    new HAProteger("1234", "dueno", true, true, true), haProtegido);
            haNombreDialogo = "m008-contrasena-dialogo";
            haFx(() -> haControlador.haAbrirArchivos(List.of(haProtegido)));
            haEsperar(2500);
            haFx(() -> haLlamar(haControlador, "haAbrir", haProtegido, "1234"));
            haEsperar(3000);
            haQuitarAvisos();
            haFoto("m009-protegido-abierto");
            haCerrarActual();

            haActivar("demo-12-paginas.pdf");
            haPulsarTooltip("Acercar");
            haPulsarTooltip("Acercar");
            haEsperar(800);
            haFoto("m010-zoom");
            haPulsarTooltip("Ajustar a ancho");
            haEsperar(800);
            haFoto("m011-ajustar-ancho");
            haFx(() -> {
                TextField haT = (TextField) haEscena.lookup("#haTxtBuscar");
                haT.setText("accion numero 105");
                haT.fireEvent(new ActionEvent());
            });
            haEsperar(1500);
            haFoto("m012-busqueda");
            haFx(() -> {
                TabPane haTabs = (TabPane) haEscena.lookup("#tabsIzquierda");
                haTabs.getSelectionModel().select(1);
            });
            haEsperar(800);
            haFoto("m013-marcadores");
            haFx(() -> ((TabPane) haEscena.lookup("#tabsIzquierda")).getSelectionModel().select(0));
            haFx(() -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet()));
            haEsperar(1200);
            haFoto("m014-modo-oscuro");
            haFx(() -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet()));
            haEsperar(800);

            haSeleccionarPagina(1);
            haFoto("m015-pagina-seleccionada");
            haPulsarTooltip("Rotar a la derecha");
            haEsperar(3500);
            haFoto("m016-rotar-resultado");
            haQuitarAvisos();
            haSeleccionarPagina(3);
            haPulsarTooltip("Mover hacia arriba");
            haEsperar(3500);
            haFoto("m017-mover-resultado");
            haQuitarAvisos();
            haSeleccionarPagina(2);
            haPulsarTooltip("Duplicar");
            haEsperar(3500);
            haFoto("m018-duplicar-resultado");
            haQuitarAvisos();
            haSeleccionarPagina(2);
            haPulsarTooltip("Eliminar");
            haEsperar(1200);
            haFoto("m019-eliminar-confirmar");
            haPulsar("Eliminar");
            haEsperar(3500);
            haFoto("m020-eliminar-resultado");
            haQuitarAvisos();
            haFoto("m021-deshacer-disponible");
            haPulsarTooltip("Deshacer");
            haEsperar(2500);
            haQuitarAvisos();
            haFoto("m022-deshacer-hecho");
            haSeleccionarPagina(0);
            haPulsarTooltip("Extraer");
            haEsperar(1200);
            haFoto("m023-extraer-formulario");
            haLimpiarCapas();
            haEsperar(600);

            haMenu("haMenuHerramientas", "Unir PDF…");
            haEsperar(1200);
            haFoto("m024-unir-formulario");
            haLimpiarCapas();
            File haDoce = haEjemplos.resolve("demo-12-paginas.pdf").toFile();
            File haSens = haEjemplos.resolve("demo-datos-sensibles.pdf").toFile();
            haFx(() -> haControlador.haAplicar("Unir 2 PDF", (haE, haK, haS, haC) -> new HAMotorPdf().haEjecutar(haE, haK,
                    new HAUnir(List.of(new HAUnir.HAFuente("actual", null, null, true), new HAUnir.HAFuente("otro", haSens, null, false))),
                    haS, haC), haR -> { }));
            haEsperar(4500);
            haFoto("m025-unir-resultado");
            haQuitarAvisos();
            haPulsarTooltip("Deshacer");
            haEsperar(2000);
            haQuitarAvisos();

            haMenu("haMenuHerramientas", "Dividir…");
            haEsperar(1200);
            haFoto("m026-dividir-formulario");
            haLimpiarCapas();
            File haCarpetaDiv = Files.createDirectories(haTrabajo.resolve("partes")).toFile();
            haFx(() -> haControlador.haExportar("Dividir", haC -> new HADivisor(new HAMotorPdf()).haDividir(haDoce, null,
                    List.of(new HAPlanDivision.HAParte("demo-12-paginas - págs 1-6", List.of(0, 1, 2, 3, 4, 5)),
                            new HAPlanDivision.HAParte("demo-12-paginas - págs 7-12", List.of(6, 7, 8, 9, 10, 11))), haCarpetaDiv, haC),
                    new HAEsperadoArchivos.HAPartes(haDoce, null, List.of(List.of(0, 1, 2, 3, 4, 5), List.of(6, 7, 8, 9, 10, 11)))));
            haEsperar(4000);
            haFoto("m027-dividir-resultado");
            haQuitarAvisos();

            haMenu("haMenuHerramientas", "Comprimir…");
            haEsperar(1200);
            haFoto("m028-comprimir-nivel");
            haElegirRadio("Que pese menos de...");
            haFx(() -> haTodos(haEscena.getRoot()).stream().filter(haN -> haN instanceof TextField haT && "2".equals(haT.getText()))
                    .map(haN -> (TextField) haN).findFirst().ifPresent(haT -> haT.setText("1")));
            haEsperar(500);
            haFoto("m029-comprimir-menos-de");
            haLimpiarCapas();
            haEsperar(600);

            haMenu("haMenuHerramientas", "Numerar páginas…");
            haEsperar(1200);
            haFoto("m030-numerar-formulario");
            haPulsar("Numerar");
            haEsperar(4500);
            haFoto("m031-numerar-resultado");
            haQuitarAvisos();
            haFx(() -> haLlamar(haControlador, "haIrAPagina", 0));
            haDesplazarVisor(1.0);
            haEsperar(1000);
            haFoto("m032-numerar-pie");
            haDesplazarVisor(0.0);
            haPulsarTooltip("Deshacer");
            haEsperar(2000);
            haQuitarAvisos();

            haMenu("haMenuHerramientas", "Marca de agua…");
            haEsperar(1200);
            haFoto("m033-marca-formulario");
            haPulsar("Aplicar");
            haEsperar(4500);
            haQuitarAvisos();
            haFoto("m034-marca-resultado");
            haPulsarTooltip("Deshacer");
            haEsperar(2000);
            haQuitarAvisos();

            haMenu("haMenuHerramientas", "Encabezado y pie…");
            haEsperar(1200);
            haFoto("m035-encabezado-formulario");
            haLimpiarCapas();
            haMenu("haMenuHerramientas", "Tamaño de página…");
            haEsperar(1200);
            haFoto("m036-tamano-formulario");
            haLimpiarCapas();
            haEsperar(600);

            haAbrir("demo-escaneado-girado.pdf");
            haEsperar(1500);
            haFoto("m037-ocr-aviso-escaneado");
            haQuitarAvisos();
            haMenu("haMenuHerramientas", "Reconocer texto (OCR)…");
            haEsperar(1200);
            haFoto("m038-ocr-formulario");
            haPulsar("Reconocer");
            haEsperar(15000);
            haFoto("m039-ocr-resultado");
            haQuitarAvisos();
            haFx(() -> {
                TextField haT = (TextField) haEscena.lookup("#haTxtBuscar");
                haT.setText("delegados");
                haT.fireEvent(new ActionEvent());
            });
            haEsperar(2000);
            haFoto("m040-ocr-busqueda");
            haCerrarActual();

            haActivar("demo-12-paginas.pdf");
            haMenu("haMenuHerramientas", "Leer facturas (QR) a Excel…");
            haEsperar(1200);
            haFoto("m041-facturas-formulario");
            haLimpiarCapas();
            File[] haFacturas = haEjemplos.resolve("facturas").toFile().listFiles((haD, haN) -> haN.endsWith(".pdf"));
            java.util.Arrays.sort(haFacturas);
            var haLista = haContexto.getBean(pe.pdflisto.facturas.HALectorFacturas.class)
                    .haLeer(List.of(haFacturas), HAControl.haNinguno());
            Stage haVentanaFacturas = haFx(() -> {
                try {
                    Class<?> haC = Class.forName("pe.pdflisto.controller.HAVentanaFacturas");
                    Method haM = java.util.Arrays.stream(haC.getDeclaredMethods()).filter(haX -> haX.getName().equals("haCrear"))
                            .findFirst().orElseThrow();
                    haM.setAccessible(true);
                    return (Stage) haM.invoke(null, null, haLista,
                            haContexto.getBean(pe.pdflisto.facturas.HAExportadorFacturas.class),
                            haContexto.getBean(pe.pdflisto.ui.HAExportador.class),
                            haContexto.getBean(pe.pdflisto.facturas.HAVerificadorExcelFacturas.class));
                } catch (Exception haE) {
                    throw new IllegalStateException(haE);
                }
            });
            Scene haPrincipal = haEscena;
            haEscena = haVentanaFacturas.getScene();
            haEsperar(1500);
            haFoto("m042-facturas-resultado");
            File haExcel = haTrabajo.resolve("Comprobantes.xlsx").toFile();
            haContexto.getBean(pe.pdflisto.facturas.HAExportadorFacturas.class).haExportar(haLista, haExcel);
            var haInforme = haContexto.getBean(pe.pdflisto.facturas.HAVerificadorExcelFacturas.class).haVerificar(haExcel, haLista);
            haFx(() -> {
                try {
                    Class<?> haC = Class.forName("pe.pdflisto.controller.HAVentanaFacturas");
                    Method haM = haC.getDeclaredMethod("haMostrarVerificacion", javafx.scene.layout.VBox.class,
                            pe.pdflisto.verificacion.HAInformeVerificacion.class);
                    haM.setAccessible(true);
                    javafx.scene.layout.BorderPane haRaiz = (javafx.scene.layout.BorderPane) haEscena.getRoot();
                    javafx.scene.layout.VBox haArriba = (javafx.scene.layout.VBox) haRaiz.getTop();
                    haM.invoke(null, (javafx.scene.layout.VBox) haArriba.getChildren().get(1), haInforme);
                } catch (Exception haE) {
                    throw new IllegalStateException(haE);
                }
            });
            haEsperar(800);
            haFoto("m043-facturas-excel-verificado");
            haEscena = haPrincipal;

            File haImg1 = haTrabajo.resolve("foto-1.png").toFile();
            File haImg2 = haTrabajo.resolve("foto-2.png").toFile();
            List<File> haImgs = haContexto.getBean(HAConversor.class).haPdfAImagenes(haDoce, null, "foto", 72, false,
                    haTrabajo.toFile(), HAControl.haNinguno());
            haImgs.get(0).renameTo(haImg1);
            haImgs.get(1).renameTo(haImg2);
            File haPdfFotos = haTrabajo.resolve("fotos.pdf").toFile();
            haFx(() -> haControlador.haExportar("Imágenes a PDF", haC -> List.of(haContexto.getBean(HAConversor.class)
                    .haImagenesAPdf(List.of(haImg1, haImg2), HAConversor.HATamanoHoja.A4, haPdfFotos, haC)),
                    new HAEsperadoArchivos.HAImagenesAPdf(List.of(haImg1, haImg2)), haF -> haControlador.haAbrirPdf(haF)));
            haEsperar(4500);
            haFoto("m044-imagenes-a-pdf-resultado");
            haQuitarAvisos();
            haCerrarActual();
            File haTxt = haTrabajo.resolve("notas.txt").toFile();
            Files.writeString(haTxt.toPath(), "Acta de la reunión\n\nAsistentes: equipo de proyecto.\nAcuerdos:\n"
                    + "1. Entregar el informe el viernes.\n2. Revisar el presupuesto.\n", StandardCharsets.UTF_8);
            File haPdfTxt = haTrabajo.resolve("notas.pdf").toFile();
            haFx(() -> haControlador.haExportar("Texto a PDF", haC -> List.of(haContexto.getBean(HAConversor.class)
                    .haTextoAPdf(haTxt, haPdfTxt, haC)), new HAEsperadoArchivos.HATextoAPdf(haTxt), haF -> haControlador.haAbrirPdf(haF)));
            haEsperar(4500);
            haQuitarAvisos();
            haFoto("m045-texto-a-pdf-resultado");
            haCerrarActual();
            haActivar("demo-12-paginas.pdf");
            haMenu("haMenuConvertir", "PDF a imágenes…");
            haEsperar(1200);
            haFoto("m047-pdf-a-imagenes-formulario");
            haLimpiarCapas();
            File haCarpetaImg = Files.createDirectories(haTrabajo.resolve("imagenes")).toFile();
            haFx(() -> haControlador.haExportar("PDF a imágenes", haC -> haContexto.getBean(HAConversor.class)
                    .haPdfAImagenes(haDoce, null, 150, false, haCarpetaImg, haC), new HAEsperadoArchivos.HAPdfAImagenes(haDoce, null, 150)));
            haEsperar(5000);
            haFoto("m048-pdf-a-imagenes-resultado");
            haQuitarAvisos();
            File haTxtSalida = haTrabajo.resolve("demo-12-paginas.txt").toFile();
            haFx(() -> haControlador.haExportar("PDF a texto", haC -> List.of(haContexto.getBean(HAConversor.class)
                    .haPdfATexto(haDoce, null, haTxtSalida, haC)), new HAEsperadoArchivos.HAPdfATexto(haDoce, null)));
            haEsperar(3000);
            haFoto("m049-pdf-a-texto-resultado");
            haQuitarAvisos();
            File haEsc = haEjemplos.resolve("demo-escaneado.pdf").toFile();
            File haCarpetaExt = Files.createDirectories(haTrabajo.resolve("extraidas")).toFile();
            haFx(() -> haControlador.haExportar("Extraer imágenes", haC -> haContexto.getBean(HAConversor.class)
                    .haExtraerImagenes(haEsc, null, haCarpetaExt, haC), new HAEsperadoArchivos.HAExtraerImagenes(haEsc, null)));
            haEsperar(3500);
            haFoto("m050-extraer-imagenes-resultado");
            haQuitarAvisos();

            haPestanaDerecha("Pedido");
            haTextoPedido("comprime a menos de 2 MB y numera");
            haFoto("m051-pedido-escrito");
            haPulsar("Ver plan");
            haEsperar(1500);
            haFoto("m052-pedido-plan");
            haPulsar("Aprobar y ejecutar");
            haEsperar(7000);
            haFoto("m053-pedido-antes-despues");
            haQuitarAvisos();
            haDesplazarPanelDerecho(0.6);
            haFoto("m054-pedido-informe");
            haDesplazarPanelDerecho(0.0);
            haPulsar("Deshacer");
            haEsperar(2500);
            haQuitarAvisos();
            haTextoPedido("marca de agua CONFIDENCIAL y elimina la última página");
            haPulsar("Ver plan");
            haEsperar(1500);
            haFoto("m055-plan-dos-pasos");
            haFx(() -> haTodos(haEscena.getRoot()).stream().filter(haN -> haN.getStyleClass().contains("paso-plan")).findFirst()
                    .flatMap(haF -> haTodos(haF).stream().filter(haN -> haN instanceof ButtonBase).map(haN -> (ButtonBase) haN).findFirst())
                    .ifPresent(ButtonBase::fire));
            haEsperar(800);
            haFoto("m056-plan-paso-quitado");
            haTextoPedido("comprme y rtoa la página 3 a la derecha");
            haPulsar("Ver plan");
            haEsperar(1500);
            haFoto("m057-quisiste-decir");
            haPulsar("Sí: «rota»");
            haEsperar(1500);
            haFoto("m058-quisiste-decir-aceptado");
            haTextoPedido("");
            haPulsar("Verificar documento actual");
            haEsperar(4000);
            haDesplazarPanelDerecho(0.5);
            haFoto("m059-verificar-documento");
            haDesplazarPanelDerecho(0.0);

            haAbrir("demo-datos-sensibles.pdf");
            haPestanaDerecha("Pedido");
            haEsperar(600);
            haPulsar("Listo para enviar");
            haEsperar(5000);
            haFoto("m060-listo-semaforo-rojo");
            haPulsar("Aprobar y arreglar");
            haEsperar(15000);
            haFx(() -> ((javafx.scene.layout.Pane) haEscena.lookup("#haZonaNotificaciones")).getChildren().clear());
            haFoto("m061-listo-semaforo-verde");
            haLimpiarCapas();
            haEsperar(600);
            haCerrarActual();

            haActivar("demo-12-paginas.pdf");
            haPestanaDerecha("Anotar");
            haEsperar(800);
            haPulsarTooltip("Resaltar");
            haEsperar(500);
            haFoto("m062-anotar-herramientas");
            haPulsarTooltip("Firma");
            haEsperar(500);
            haFoto("m063-anotar-firma");
            haNombreDialogo = "m064-firma-dibujar";
            haPestanaDialogo = 0;
            haPulsar("Nueva firma…");
            haEsperar(1200);
            haNombreDialogo = "m065-firma-escribir";
            haPestanaDialogo = 1;
            haPulsar("Nueva firma…");
            haEsperar(1200);
            haNombreDialogo = "m066-firma-imagen";
            haPestanaDialogo = 2;
            haPulsar("Nueva firma…");
            haEsperar(1200);
            File haLogo = haTrabajo.resolve("logo.png").toFile();
            BufferedImage haL = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D haG = haL.createGraphics();
            haG.setColor(new Color(30, 90, 200));
            haG.fillRoundRect(0, 0, 240, 120, 30, 30);
            haG.setColor(Color.WHITE);
            haG.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 34));
            haG.drawString("MI LOGO", 42, 74);
            haG.dispose();
            ImageIO.write(haL, "png", haLogo);
            File haFirma = haTrabajo.resolve("firma.png").toFile();
            BufferedImage haFi = new BufferedImage(300, 110, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D haGf = haFi.createGraphics();
            haGf.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            haGf.setColor(new Color(20, 40, 120));
            haGf.setFont(new java.awt.Font("Segoe Script", java.awt.Font.PLAIN, 40));
            haGf.drawString("H. Ataucusi", 10, 70);
            haGf.dispose();
            ImageIO.write(haFi, "png", haFirma);
            List<HAAnotacion> haAnot = List.of(
                    new HAAnotacion.HAMarcado(HAAnotacion.HATipoMarcado.RESALTADO, 0, List.of(new HAAreaTexto(70, 104, 300, 16)), Color.YELLOW, null),
                    new HAAnotacion.HAMarcado(HAAnotacion.HATipoMarcado.SUBRAYADO, 0, List.of(new HAAreaTexto(70, 126, 300, 16)), Color.BLUE, null),
                    new HAAnotacion.HAMarcado(HAAnotacion.HATipoMarcado.TACHADO, 0, List.of(new HAAreaTexto(70, 148, 300, 16)), Color.RED, null),
                    new HAAnotacion.HANota(0, 420, 110, "Revisar esta línea", Color.ORANGE),
                    new HAAnotacion.HAForma(HAAnotacion.HATipoForma.RECTANGULO, 0, 66, 186, 380, 228, Color.RED, 2f),
                    new HAAnotacion.HAForma(HAAnotacion.HATipoForma.ELIPSE, 0, 400, 180, 520, 240, Color.GREEN, 2f),
                    new HAAnotacion.HAForma(HAAnotacion.HATipoForma.FLECHA, 0, 400, 300, 330, 260, Color.RED, 2f),
                    new HAAnotacion.HATrazo(0, List.of(new double[]{80, 320}, new double[]{110, 300}, new double[]{140, 330},
                            new double[]{170, 300}, new double[]{200, 330}), new Color(120, 0, 160), 2f),
                    new HAAnotacion.HATexto(0, 80, 380, "Texto agregado con PDF Listo", 14, Color.BLUE),
                    new HAAnotacion.HAImagen(0, new HAAreaTexto(380, 360, 140, 70), haLogo),
                    new HAAnotacion.HASello(0, new HAAreaTexto(80, 450, 170, 60), "APROBADO", Color.RED, "25/11/2026"),
                    new HAAnotacion.HATexto(0, 300, 470, "25/11/2026", 12, Color.BLACK),
                    new HAAnotacion.HAFirma(0, new HAAreaTexto(330, 520, 180, 66), haFirma, "Mi firma"));
            haFx(() -> haControlador.haAplicar("Anotar", (haE, haK, haS, haC) -> new HAMotorPdf().haEjecutar(haE, haK,
                    new HAAnotar(haAnot, "Prueba"), haS, haC), haR -> { }));
            haEsperar(5000);
            haQuitarAvisos();
            haFx(() -> haLlamar(haControlador, "haIrAPagina", 0));
            haEsperar(1500);
            haFoto("m067-anotaciones-resultado");
            haPulsarTooltip("Deshacer");
            haEsperar(2000);
            haQuitarAvisos();

            haAbrir("demo-formulario.pdf");
            haPestanaDerecha("Formulario");
            haEsperar(1200);
            haFoto("m068-formulario-vacio");
            haFx(() -> {
                List<TextField> haCampos = haTodos(haEscena.lookup("#haContenedorFormulario")).stream()
                        .filter(haN -> haN instanceof TextField).map(haN -> (TextField) haN).toList();
                String[] haValores = {"Ana Torres Quispe", "45678912", "Miraflores"};
                for (int haI = 0; haI < haCampos.size() && haI < haValores.length; haI++) {
                    haCampos.get(haI).setText(haValores[haI]);
                }
                haTodos(haEscena.lookup("#haContenedorFormulario")).stream().filter(haN -> haN instanceof CheckBox)
                        .map(haN -> (CheckBox) haN).forEach(haC -> haC.setSelected(true));
            });
            haEsperar(600);
            haFoto("m069-formulario-lleno");
            haPulsar("Guardar formulario");
            haEsperar(4500);
            haQuitarAvisos();
            haFoto("m070-formulario-resultado");
            haCerrarActual();

            haAbrir("demo-escaneado.pdf");
            haPestanaDerecha("Entrega");
            haEsperar(800);
            haPulsar("Validar");
            haEsperar(4000);
            haFoto("m071-perfil-validar");
            haPulsar("Corregir…");
            haEsperar(1500);
            haFoto("m072-perfil-corregir");
            haPulsar("Cancelar");
            haLimpiarCapas();
            haActivar("demo-12-paginas.pdf");
            haPestanaDerecha("Entrega");
            haPulsar("Nueva receta…");
            haEsperar(1200);
            haFoto("m073-nueva-receta");
            haLimpiarCapas();
            haEsperar(600);
            var haHistorial = haContexto.getBean(pe.pdflisto.service.HAHistorialService.class);
            String haComb = "numerar|comprimir:MEDIA";
            try {
                pe.pdflisto.interprete.HASerializadorPasos.haDecodificar(haComb, List.of());
            } catch (RuntimeException haE) {
                haComb = null;
            }
            if (haComb == null) {
                haTextoPedido("comprime y numera");
                haPestanaDerecha("Pedido");
                haPulsar("Ver plan");
                haEsperar(1200);
                haPulsar("Aprobar y ejecutar");
                haEsperar(7000);
                haQuitarAvisos();
                haComb = haFx(() -> haControlador.haSesion().haCodigosAplicados());
            }
            for (int haI = 0; haI < 3; haI++) {
                haHistorial.haRegistrar(haDoce, "Guardó una copia", null, haDoce, null, haComb);
            }
            String haCombFinal = haComb;
            haFx(() -> haContexto.getBean(pe.pdflisto.controller.HAPanelEntrega.class).haDespuesDeGuardar(haCombFinal));
            haEsperar(1000);
            haFoto("m074-aprendizaje-receta");
            haPulsar("No, gracias");
            haEsperar(600);
            haQuitarAvisos();
            haPestanaDerecha("Entrega");
            haDesplazarPanelDerecho(1.0);
            haEsperar(600);
            haFoto("m075-nombre-automatico");
            haDesplazarPanelDerecho(0.0);

            haAbrir("demo-datos-sensibles.pdf");
            haPestanaDerecha("Tachado");
            haEsperar(600);
            haPulsar("Buscar datos sensibles");
            haEsperar(5000);
            haQuitarAvisos();
            haFoto("m076-tachado-encontrados");
            haPulsarTexto("Tachar un área a mano");
            haEsperar(500);
            haFoto("m077-tachado-area-modo");
            Object haPanelTachado = haContexto.getBean(pe.pdflisto.controller.HAPanelTachado.class);
            haFx(() -> haLlamar(haPanelTachado, "haAgregarManual", 0, new HAAreaTexto(70, 610, 320, 22)));
            haPulsarTexto("Tachar un área a mano");
            haEsperar(800);
            haDesplazarPanelDerecho(1.0);
            haFoto("m078-tachado-area-agregada");
            haDesplazarPanelDerecho(0.0);
            haPulsarInicio("Tachar y verificar");
            haEsperar(1200);
            haFoto("m079-tachado-confirmar");
            haPulsar("Tachar");
            haEsperar(9000);
            haQuitarAvisos();
            haFoto("m080-tachado-resultado");
            haPulsarTooltip("Deshacer");
            haEsperar(2500);
            haQuitarAvisos();
            haPestanaDerecha("Tachado");
            haDesplazarPanelDerecho(1.0);
            haEsperar(600);
            haFoto("m081-tachado-otras-herramientas");
            haPulsar("Informe de privacidad");
            haEsperar(4000);
            haFoto("m082-informe-privacidad");
            haLimpiarCapas();
            haEsperar(600);
            haPulsar("Limpiar metadatos…");
            haEsperar(2500);
            haFoto("m083-limpiar-metadatos");
            haPulsar("Limpiar");
            haEsperar(1500);
            haFoto("m084-limpiar-metadatos-confirmar");
            haLimpiarCapas();
            haEsperar(600);
            haLimpiarCapas();
            haEsperar(600);
            haPulsar("Poner contraseña…");
            haEsperar(1500);
            haFx(() -> haTodos(haEscena.getRoot()).stream().filter(haN -> haN instanceof javafx.scene.control.PasswordField)
                    .map(haN -> (TextInputControl) haN).forEach(haT -> haT.setText("1234")));
            haEsperar(400);
            haFoto("m085-poner-contrasena");
            haPulsar("Proteger");
            haEsperar(5000);
            haQuitarAvisos();
            haFoto("m086-contrasena-puesta");
            haPestanaDerecha("Tachado");
            haDesplazarPanelDerecho(1.0);
            haPulsar("Quitar contraseña");
            haEsperar(1200);
            haPulsar("Continuar");
            haEsperar(5000);
            haQuitarAvisos();
            haFoto("m087-contrasena-quitada");

            haPestanaDerecha("Historial");
            haEsperar(1200);
            haFoto("m088-historial");
            haPulsarTooltip("Repetir en el documento abierto");
            haEsperar(1500);
            haFoto("m089-historial-repetir");

            haPestanaDerecha("Entrega");
            File haCopia = haTrabajo.resolve("demo-datos-sensibles (editado).pdf").toFile();
            haFx(() -> {
                HASesionEdicion haS = haControlador.haSesion();
                haContexto.getBean(pe.pdflisto.pdf.HAPdfService.class).haGuardarCopia(haS.haVista(), haCopia, haS.haOriginal());
                haS.haMarcarGuardado();
                haLlamar(haControlador, "haActualizarBarraSuperior");
                haContexto.getBean(HANotificador.class).haExito("Copia guardada y verificada: " + haCopia.getName());
            });
            haEsperar(1200);
            haFoto("m090-copia-guardada");
            haQuitarAvisos();

            haMenu("haMenuAyuda", "Comprobar componentes…");
            haEsperar(6000);
            haFoto("m091-componentes");
            haLimpiarCapas();
            haEsperar(600);
            File haZip = haTrabajo.resolve("PDFListo-diagnostico.zip").toFile();
            var haDiag = new pe.pdflisto.sistema.HADiagnostico().haCrear(haZip.toPath(), "1.0.1",
                    haContexto.getBean(pe.pdflisto.sistema.HAComprobadorComponentes.class).haComprobar(),
                    haDatos.resolve("logs"), haDatos);
            haFx(() -> haContexto.getBean(HANotificador.class).haExito("Diagnóstico guardado: " + haZip.getName() + " ("
                    + pe.pdflisto.operaciones.HAArchivos.haTamanoLegible(haDiag.haBytes()) + "). No incluye tus documentos ni sus "
                    + "nombres; puedes revisarlo antes de enviarlo."));
            haEsperar(800);
            haFoto("m092-diagnostico");
            haQuitarAvisos();
            haMenu("haMenuAyuda", "Acerca de PDF Listo");
            haEsperar(1500);
            haFoto("m093-acerca-de");
            haLimpiarCapas();
            System.out.println("Capturas completas listas en " + haSalida.toAbsolutePath());
        } catch (Throwable haE) {
            haE.printStackTrace();
        } finally {
            Platform.exit();
            haContexto.close();
            System.exit(0);
        }
    }

    private void haAbrir(String haNombre) throws Exception {
        haLimpiarCapas();
        haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve(haNombre).toFile())));
        haEsperar(4000);
        haQuitarAvisos();
    }

    private void haActivar(String haNombre) throws Exception {
        haLimpiarCapas();
        haFx(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<Object> haCmb = (ComboBox<Object>) haEscena.lookup("#haCmbDocumentos");
            haCmb.getItems().stream().filter(haI -> String.valueOf(haI).contains(haNombre)).findFirst()
                    .ifPresent(haI -> haCmb.getSelectionModel().select(haI));
        });
        haEsperar(2000);
        haQuitarAvisos();
    }

    private void haCerrarActual() throws Exception {
        haFx(() -> haLlamar(haControlador, "haOnCerrarDocumento"));
        haEsperar(1000);
        haPulsar("Cerrar sin guardar");
        haEsperar(800);
        haQuitarAvisos();
    }

    private void haCerrarTodo() throws Exception {
        for (int haI = 0; haI < 6; haI++) {
            boolean haHay = haFx(() -> haControlador.haSesion() != null);
            if (!haHay) {
                return;
            }
            haCerrarActual();
        }
    }

    private void haSeleccionarPagina(int haI) throws Exception {
        haFx(() -> {
            @SuppressWarnings("unchecked")
            ListView<Integer> haL = (ListView<Integer>) haEscena.lookup("#haListaMiniaturas");
            haL.getSelectionModel().clearAndSelect(haI);
        });
        haEsperar(1000);
    }

    private void haTextoPedido(String haTexto) throws Exception {
        haPestanaDerecha("Pedido");
        haFx(() -> haTodos(haEscena.getRoot()).stream().filter(haN -> haN instanceof TextArea).map(haN -> (TextArea) haN)
                .findFirst().ifPresent(haT -> haT.setText(haTexto)));
        haEsperar(300);
    }

    private void haDesplazarVisor(double haV) throws Exception {
        haFx(() -> ((ScrollPane) haEscena.lookup("#haScrollVisor")).setVvalue(haV));
        haEsperar(500);
    }

    private void haElegirRadio(String haTexto) throws Exception {
        haFx(() -> haTodos(haEscena.getRoot()).stream().filter(haN -> haN instanceof RadioButton haR
                && haR.getText() != null && haR.getText().startsWith(haTexto.replace("...", ""))).map(haN -> (RadioButton) haN)
                .findFirst().ifPresent(haR -> haR.setSelected(true)));
    }

    private void haFotoDialogo(Dialog<?> haDialogo) {
        try {
            var haPanel = haDialogo.getDialogPane();
            haTodos(haPanel).stream().filter(haN -> haN instanceof TabPane).map(haN -> (TabPane) haN).findFirst()
                    .ifPresent(haT -> haT.getSelectionModel().select(Math.min(haPestanaDialogo, haT.getTabs().size() - 1)));
            javafx.scene.layout.VBox haMarco = new javafx.scene.layout.VBox(new javafx.scene.control.Label(haDialogo.getTitle()), haPanel);
            haMarco.setStyle("-fx-padding: 0; -fx-background-color: -color-bg-default; -fx-border-color: -color-border-default;");
            haMarco.getChildren().get(0).setStyle("-fx-padding: 8 12 8 12; -fx-font-weight: bold;");
            Scene haS = new Scene(haMarco);
            haS.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
            haMarco.applyCss();
            haMarco.layout();
            haGuardar(haS.snapshot(null), haNombreDialogo == null ? "dialogo" : haNombreDialogo);
        } catch (Exception haE) {
            haE.printStackTrace();
        }
    }

    private void haFoto(String haNombre) throws Exception {
        WritableImage haImg = haFx(() -> {
            haEscena.getRoot().applyCss();
            haEscena.getRoot().layout();
            return haEscena.snapshot(null);
        });
        haGuardar(haImg, haNombre);
    }

    private void haGuardar(WritableImage haImg, String haNombre) throws Exception {
        int haW = (int) haImg.getWidth();
        int haH = (int) haImg.getHeight();
        BufferedImage haBi = new BufferedImage(haW, haH, BufferedImage.TYPE_INT_RGB);
        PixelReader haPr = haImg.getPixelReader();
        for (int haY = 0; haY < haH; haY++) {
            for (int haX = 0; haX < haW; haX++) {
                haBi.setRGB(haX, haY, haPr.getArgb(haX, haY));
            }
        }
        File haF = haSalida.resolve(haNombre + ".png").toFile();
        ImageIO.write(haBi, "png", haF);
        System.out.println("captura: " + haF.getName());
    }

    private void haQuitarAvisos() throws Exception {
        haFx(() -> {
            ((javafx.scene.layout.Pane) haEscena.lookup("#haZonaNotificaciones")).getChildren().clear();
            haContexto.getBean(HANotificador.class).haCerrarModal();
        });
    }

    private void haLimpiarCapas() throws Exception {
        haQuitarAvisos();
        haFx(() -> haContexto.getBean(HANotificador.class).haCerrarPanel());
        haEsperar(500);
    }

    private void haPulsarInicio(String haInicio) throws Exception {
        haFx(() -> haTodos(haEscena.getRoot()).stream()
                .filter(haN -> haN instanceof ButtonBase haBb && haBb.getText() != null && haBb.getText().startsWith(haInicio)
                        && !haBb.isDisabled())
                .map(haN -> (ButtonBase) haN).findFirst().ifPresent(ButtonBase::fire));
    }

    private void haPulsar(String haTexto) throws Exception {
        boolean haHecho = haFx(() -> {
            Optional<ButtonBase> haB = haTodos(haEscena.getRoot()).stream()
                    .filter(haN -> haN instanceof ButtonBase haBb && haTexto.equals(haBb.getText()) && !haBb.isDisabled()
                            && haBb.isVisible())
                    .map(haN -> (ButtonBase) haN).reduce((haA, haC) -> haC);
            haB.ifPresent(ButtonBase::fire);
            return haB.isPresent();
        });
        if (!haHecho) {
            System.out.println("AVISO: no se encontro el boton «" + haTexto + "»");
        }
    }

    private void haPulsarTexto(String haTexto) throws Exception {
        haPulsar(haTexto);
    }

    private void haPulsarTooltip(String haInicio) throws Exception {
        boolean haHecho = haFx(() -> {
            Optional<ButtonBase> haB = haTodos(haEscena.getRoot()).stream()
                    .filter(haN -> haN instanceof ButtonBase haBb && haBb.getTooltip() != null
                            && haBb.getTooltip().getText().startsWith(haInicio) && !haBb.isDisabled())
                    .map(haN -> (ButtonBase) haN).findFirst();
            haB.ifPresent(ButtonBase::fire);
            return haB.isPresent();
        });
        if (!haHecho) {
            System.out.println("AVISO: no se encontro el boton con ayuda «" + haInicio + "»");
        }
    }

    private void haPestanaDerecha(String haTexto) throws Exception {
        haFx(() -> haContexto.getBean(HANotificador.class).haCerrarPanel());
        haFx(() -> {
            TabPane haPanel = (TabPane) haEscena.lookup("#haPanelDerecho");
            for (Tab haT : haPanel.getTabs()) {
                String haNombre = haT.getText() != null ? haT.getText()
                        : haT.getTooltip() != null ? haT.getTooltip().getText() : "";
                if (haNombre.startsWith(haTexto) || (haT.getUserData() != null && haT.getUserData().toString().startsWith(haTexto))) {
                    haPanel.getSelectionModel().select(haT);
                }
            }
        });
        haEsperar(500);
    }

    private void haDesplazarPanelDerecho(double haV) throws Exception {
        haFx(() -> {
            TabPane haPanel = (TabPane) haEscena.lookup("#haPanelDerecho");
            haTodos(haPanel.getSelectionModel().getSelectedItem().getContent()).stream()
                    .filter(haN -> haN instanceof ScrollPane).map(haN -> (ScrollPane) haN).findFirst()
                    .ifPresent(haS -> haS.setVvalue(haV));
        });
        haEsperar(400);
    }

    private void haMenu(String haId, String haTexto) throws Exception {
        haLimpiarCapas();
        haFx(() -> {
            MenuButton haM = (MenuButton) haEscena.lookup("#" + haId);
            if (haM.getOnShowing() != null) {
                haM.getOnShowing().handle(null);
            }
            for (MenuItem haI : haM.getItems()) {
                if (haTexto.equals(haI.getText()) && !haI.isDisable()) {
                    haI.fire();
                }
            }
        });
    }

    private static Object haLlamar(Object haObjeto, String haMetodo, Object... haArgs) {
        try {
            for (Method haM : haObjeto.getClass().getDeclaredMethods()) {
                if (haM.getName().equals(haMetodo) && haM.getParameterCount() == haArgs.length) {
                    haM.setAccessible(true);
                    return haM.invoke(haObjeto, haArgs);
                }
            }
            throw new IllegalArgumentException("No existe " + haMetodo);
        } catch (Exception haE) {
            throw new IllegalStateException(haE);
        }
    }

    private static List<Node> haTodos(Node haRaiz) {
        List<Node> haLista = new ArrayList<>();
        haRecorrer(haRaiz, haLista);
        return haLista;
    }

    private static void haRecorrer(Node haN, List<Node> haLista) {
        if (haN == null || haLista.contains(haN)) {
            return;
        }
        haLista.add(haN);
        if (haN instanceof TabPane haTp) {
            haTp.getTabs().forEach(haT -> haRecorrer(haT.getContent(), haLista));
        }
        if (haN instanceof ScrollPane haSp) {
            haRecorrer(haSp.getContent(), haLista);
        }
        if (haN instanceof Parent haP) {
            haP.getChildrenUnmodifiable().forEach(haC -> haRecorrer(haC, haLista));
        }
    }

    private static void haFx(Runnable haR) throws Exception {
        haFx(() -> {
            haR.run();
            return null;
        });
    }

    private static <T> T haFx(Supplier<T> haS) throws Exception {
        CompletableFuture<T> haF = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                haF.complete(haS.get());
            } catch (Throwable haE) {
                haF.completeExceptionally(haE);
            }
        });
        return haF.get(60, TimeUnit.SECONDS);
    }

    private static void haEsperar(long haMs) throws InterruptedException {
        Thread.sleep(haMs);
    }
}
