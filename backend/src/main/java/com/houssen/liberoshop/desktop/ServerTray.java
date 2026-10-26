package com.houssen.liberoshop.desktop;

import com.houssen.liberoshop.desktop.ServerMonitor.State;
import com.houssen.liberoshop.desktop.ServerMonitor.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The icon in the notification area: the only visible part of the installed application.
 *
 * <p>Installed, the server has no window and no console. The icon says whether it is up (green),
 * coming up (orange) or down (red), and its menu opens the shop in the browser, stops, starts
 * and restarts the server, launches the installer of a newer version, and quits.
 *
 * <p>Opt-in through {@value #ENABLED_PROPERTY}, which the installers set (see
 * {@code src/main/jpackage/runtime.args}): started from an IDE, from {@code java -jar} or on a
 * machine without a notification area, nothing changes and the application runs as before.
 *
 * <p>The tray starts the server itself rather than being started by it, because it has to
 * survive it: a start that fails leaves a red icon and its reason instead of a process that
 * silently went away, and "Demarrer" is still there to try again.
 */
public final class ServerTray {

    public static final String ENABLED_PROPERTY = "liberoshop.tray.enabled";
    /**
     * Passed by the Windows installer only, when it starts the application at the end of an
     * installation (see {@code src/main/jpackage/windows/main.wxs}): the shop then opens in the
     * browser once the server is up. The logon start has no such argument, so it stays silent.
     */
    public static final String OPEN_BROWSER_ARGUMENT = "--liberoshop.open-browser";

    private static final Logger log = LoggerFactory.getLogger(ServerTray.class);

    private static final String NAME = "Moussa Shop";
    /** Optional. Drop the shop logo here (a square PNG, transparent background) to have it in the tray. */
    private static final String LOGO = "/desktop/logo.png";
    private static final Color GREEN = new Color(0x2E9E4F);
    private static final Color ORANGE = new Color(0xF29900);
    private static final Color RED = new Color(0xD93025);
    /** Windows refuses a longer tooltip. */
    private static final int TOOLTIP_MAX = 127;

    private static volatile ServerTray installed;

    private final Supplier<ConfigurableApplicationContext> starter;
    private final ServerMonitor monitor = new ServerMonitor(() -> EventQueue.invokeLater(this::refresh));
    /**
     * One thread for every start and stop, so two clicks never overlap. Not a daemon: it is also
     * what keeps the process alive once a start has failed and Spring's own threads are gone.
     */
    private final ExecutorService lifecycle =
            Executors.newSingleThreadExecutor(task -> new Thread(task, "liberoshop-tray"));

    private final MenuItem statusItem = new MenuItem();
    private final MenuItem openItem = new MenuItem("Ouvrir " + NAME);
    private final MenuItem startItem = new MenuItem("Démarrer");
    private final MenuItem stopItem = new MenuItem("Arrêter");
    private final MenuItem restartItem = new MenuItem("Redémarrer");
    private final MenuItem updateItem = new MenuItem("Mettre à jour…");
    private final MenuItem quitItem = new MenuItem("Quitter");

    private TrayIcon icon;
    private final BufferedImage logo = loadLogo();
    private int iconPixels;
    private State shown;
    /** Opens the browser the first time the server is up, then never again in this process. */
    private boolean openWhenRunning;

    private ServerTray(Supplier<ConfigurableApplicationContext> starter, boolean openWhenRunning) {
        this.starter = starter;
        this.openWhenRunning = openWhenRunning;
    }

    /**
     * Shows the icon and starts the server behind it.
     *
     * @param starter how to start the application, each time it has to be
     * @param openWhenRunning open the shop in the browser once the server is up (after an installation)
     * @return false when there is no tray here; the caller then starts the application itself
     */
    public static boolean launch(Supplier<ConfigurableApplicationContext> starter, boolean openWhenRunning) {
        if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
            return false;
        }
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            log.info("Pas de zone de notification sur ce poste : demarrage sans icone.");
            return false;
        }
        // Spring Boot switches AWT to headless unless told otherwise, and the dialogs with it.
        System.setProperty("java.awt.headless", "false");

        ServerTray tray = new ServerTray(starter, openWhenRunning);
        try {
            EventQueue.invokeAndWait(tray::show);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (InvocationTargetException e) {
            log.warn("Icone de notification indisponible : demarrage sans icone.", e.getCause());
            return false;
        }
        installed = tray;
        tray.lifecycle.execute(tray::startServer);
        return true;
    }

    /** Lets the tray follow this start. Does nothing when no tray was launched. */
    public static void watch(SpringApplication application) {
        ServerTray tray = installed;
        if (tray != null) {
            application.addListeners(tray.monitor);
        }
    }

    // ------------------------------------------------------------------ icon and menu (AWT thread)

    private void show() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // The default look is only less pretty.
        }
        openItem.setFont(Font.decode(null).deriveFont(Font.BOLD));
        statusItem.setEnabled(false);

        openItem.addActionListener(event -> open());
        startItem.addActionListener(event -> lifecycle.execute(this::startServer));
        stopItem.addActionListener(event -> {
            if (confirm("Arrêter le serveur ? Les caisses et les téléphones seront déconnectés.")) {
                lifecycle.execute(monitor::stop);
            }
        });
        restartItem.addActionListener(event -> {
            if (confirm("Redémarrer le serveur ? Les caisses seront déconnectées quelques instants.")) {
                lifecycle.execute(this::restartServer);
            }
        });
        updateItem.addActionListener(event -> update());
        quitItem.addActionListener(event -> {
            if (monitor.status().state() != State.RUNNING
                    || confirm("Quitter " + NAME + " ? Le serveur sera arrêté.")) {
                quit();
            }
        });

        PopupMenu menu = new PopupMenu();
        menu.add(statusItem);
        menu.addSeparator();
        menu.add(openItem);
        menu.addSeparator();
        menu.add(startItem);
        menu.add(stopItem);
        menu.add(restartItem);
        menu.addSeparator();
        menu.add(updateItem);
        menu.addSeparator();
        menu.add(quitItem);

        SystemTray tray = SystemTray.getSystemTray();
        Dimension size = tray.getTrayIconSize();
        double scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getDefaultTransform().getScaleX();
        iconPixels = Math.max(16, (int) Math.round(size.width * scale));

        icon = new TrayIcon(paint(logo, RED, iconPixels), NAME, menu);
        icon.setImageAutoSize(true);
        // Double-click.
        icon.addActionListener(event -> open());
        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new IllegalStateException(e);
        }
        refresh();
    }

    private void refresh() {
        Status status = monitor.status();
        State state = status.state();

        if (state != shown) {
            icon.setImage(paint(logo, color(state), iconPixels));
        }
        String description = describe(status);
        statusItem.setLabel(description);
        icon.setToolTip(abbreviate(NAME + " — " + description, TOOLTIP_MAX));

        openItem.setEnabled(state == State.RUNNING);
        startItem.setEnabled(state == State.STOPPED || state == State.FAILED);
        stopItem.setEnabled(state == State.RUNNING);
        restartItem.setEnabled(state == State.RUNNING);

        if (state == State.FAILED && shown != State.FAILED) {
            icon.displayMessage(NAME, "Le serveur n'a pas démarré : " + status.failure(),
                    TrayIcon.MessageType.ERROR);
        }
        shown = state;

        if (state == State.RUNNING && openWhenRunning) {
            openWhenRunning = false;
            open();
        }
    }

    private static Color color(State state) {
        return switch (state) {
            case RUNNING -> GREEN;
            case STARTING -> ORANGE;
            case STOPPED, FAILED -> RED;
        };
    }

    private static String describe(Status status) {
        return switch (status.state()) {
            case RUNNING -> "En marche — " + status.address();
            case STARTING -> "Démarrage en cours…";
            case STOPPED -> "Arrêté";
            case FAILED -> "Erreur : " + status.failure();
        };
    }

    private static String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** The logo, or nothing: a missing or unreadable file only costs the artwork. */
    private static BufferedImage loadLogo() {
        try (InputStream in = ServerTray.class.getResourceAsStream(LOGO)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            log.warn("Logo {} illisible : icone par defaut.", LOGO, e);
            return null;
        }
    }

    /**
     * The logo with a dot in the colour of the state in its corner, the way WampServer does it.
     * Without a logo, a disc in that colour with the shop's initial.
     */
    static Image paint(BufferedImage logo, Color color, int pixels) {
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (logo != null) {
            // Fitted whole, whatever its proportions. Averaged, not sampled: the logo is far
            // larger than the sixteen or so pixels it lands on.
            double fit = Math.min((double) pixels / logo.getWidth(), (double) pixels / logo.getHeight());
            int width = Math.max(1, (int) Math.round(logo.getWidth() * fit));
            int height = Math.max(1, (int) Math.round(logo.getHeight() * fit));
            Image fitted = new ImageIcon(logo.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING))
                    .getImage();
            g.drawImage(fitted, (pixels - width) / 2, (pixels - height) / 2, null);

            float dot = pixels * 0.5f;
            float ring = Math.max(1f, pixels / 16f);
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(pixels - dot - ring, pixels - dot - ring, dot + ring, dot + ring));
            g.setColor(color);
            g.fill(new Ellipse2D.Float(pixels - dot, pixels - dot, dot - ring, dot - ring));
            g.dispose();
            return image;
        }
        g.setColor(color);
        g.fill(new Ellipse2D.Float(0.5f, 0.5f, pixels - 1f, pixels - 1f));

        g.setColor(Color.WHITE);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(pixels * 0.7f));
        GlyphVector letter = font.createGlyphVector(g.getFontRenderContext(), "L");
        Rectangle2D bounds = letter.getVisualBounds();
        g.drawGlyphVector(letter,
                (float) ((pixels - bounds.getWidth()) / 2 - bounds.getX()),
                (float) ((pixels - bounds.getHeight()) / 2 - bounds.getY()));
        g.dispose();
        return image;
    }

    private void open() {
        Status status = monitor.status();
        if (status.state() != State.RUNNING) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(status.address()));
        } catch (IOException | RuntimeException e) {
            log.warn("Impossible d'ouvrir le navigateur sur {}.", status.address(), e);
            message("Impossible d'ouvrir le navigateur. Adresse : " + status.address(), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * There is no update server: a new version arrives as an installer file. The application has
     * to be down while the installer replaces it, so it stops, hands over, and quits.
     */
    private void update() {
        FileDialog chooser = new FileDialog((Frame) null,
                "Choisir l'installateur de la nouvelle version", FileDialog.LOAD);
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            chooser.setFile("*.exe;*.msi");
        }
        chooser.setVisible(true);
        String name = chooser.getFile();
        if (name == null) {
            return;
        }
        File installer = new File(chooser.getDirectory(), name);
        if (confirm(NAME + " va s'arrêter pour installer « " + name + " ». Continuer ?")) {
            lifecycle.execute(() -> install(installer));
        }
    }

    private static boolean confirm(String question) {
        String yes = "Oui";
        JOptionPane pane = new JOptionPane(question, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.YES_NO_OPTION, null, new Object[] {yes, "Non"}, "Non");
        showOnTop(pane);
        return yes.equals(pane.getValue());
    }

    private static void message(String text, int type) {
        showOnTop(new JOptionPane(text, type));
    }

    /** There is no window to be the parent, so the dialog must not open behind the others. */
    private static void showOnTop(JOptionPane pane) {
        JDialog dialog = pane.createDialog(NAME);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
        dialog.dispose();
    }

    // ------------------------------------------------------------------ server (lifecycle thread)

    private void startServer() {
        State state = monitor.status().state();
        if (state == State.RUNNING || state == State.STARTING) {
            return;
        }
        try {
            starter.get();
        } catch (RuntimeException e) {
            // Spring has logged it; the icon shows it.
            monitor.failed(e);
        }
    }

    private void restartServer() {
        monitor.stop();
        startServer();
    }

    private void install(File installer) {
        monitor.stop();
        try {
            Desktop.getDesktop().open(installer);
        } catch (IOException | RuntimeException e) {
            log.warn("Impossible de lancer l'installateur {}.", installer, e);
            EventQueue.invokeLater(() -> message("Impossible de lancer « " + installer.getName()
                    + " ». Le serveur redémarre.", JOptionPane.ERROR_MESSAGE));
            startServer();
            return;
        }
        EventQueue.invokeLater(this::quit);
    }

    private void quit() {
        // Removed first: Windows otherwise leaves a dead icon behind until the mouse passes over it.
        SystemTray.getSystemTray().remove(icon);
        // Spring's shutdown hook closes the context, and the database with it.
        new Thread(() -> System.exit(0), "liberoshop-exit").start();
    }
}
