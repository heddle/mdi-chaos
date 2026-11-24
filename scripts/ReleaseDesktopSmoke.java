import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import edu.cnu.mdi.chaos.app.ChaosApp;
import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.DivergenceFit;
import edu.cnu.mdi.chaos.sim.ChaosSimulation;
import edu.cnu.mdi.desktop.Desktop;
import edu.cnu.mdi.mdi3D.panel.Panel3D;
import edu.cnu.mdi.splot.pdata.Curve;

/** Desktop checks loaded as a Java agent so the packaged JAR starts normally. */
public final class ReleaseDesktopSmoke {
    private static final int REOPEN_CYCLES = 16;
    private static final String STARTUP = "startup";
    // The macOS release job retries this status once.
    private static final int STARTUP_TIMEOUT = 75;
    private static String phase = STARTUP;
    private static ChaosApp app;
    public static void premain(String args) {
        new Thread(ReleaseDesktopSmoke::verifyDownload, "Packaged app verification").start();
    }

    private static void verifyDownload() {
        try {
            await(() -> {
                app = Arrays.stream(Window.getWindows()).filter(ChaosApp.class::isInstance)
                        .map(ChaosApp.class::cast).findFirst().orElse(null);
                return app != null && app.isShowing();
            });
            await(() -> label("Ready") && button("Start").isEnabled());
            phase = "desktop layout";
            await(() -> {
                var desktop = Desktop.getInstance();
                var frames = desktop.getAllFrames();
                if (frames.length != 3) return false;
                var region = new Rectangle(0, 0, desktop.getWidth(), desktop.getHeight());
                for (int i = 0; i < frames.length; i++) {
                    var view = frames[i];
                    if (!view.isShowing() || !region.contains(view.getBounds())
                            || view.getWidth() < 280 || view.getHeight() < 180) return false;
                    for (int j = i + 1; j < frames.length; j++) {
                        if (view.getBounds().intersects(frames[j].getBounds())) return false;
                    }
                }
                return true;
            });
            System.out.println("Layout passed: three visible nonoverlapping views inside the desktop.");
            check(edt(() -> field(app, "trajectories3D") == null), "2D startup never initializes 3D");
            phase = "simulation controls";
            var initial = edt(ReleaseDesktopSmoke::snapshot);
            edt(() -> { checkbox(app).doClick(); return null; });
            check(edt(() -> !lyapunov().isVisible()), "Checkbox takes effect on Reset");
            click("Reset");
            await(() -> label("Ready") && lyapunov().getText().contains("warming up"));
            click("Single step");
            await(() -> label("Paused"));
            var first = edt(ReleaseDesktopSmoke::snapshot);
            check(first.step() == 1 && first.time() == 0.005, "Step from Ready");
            click("Single step");
            await(() -> label("Paused"));
            var second = edt(ReleaseDesktopSmoke::snapshot);
            check(second.step() == 2 && second.time() == 0.010, "Step from Paused");
            Thread.sleep(150);
            check(edt(ReleaseDesktopSmoke::snapshot).equals(second), "No extra worker step");
            System.out.println("Single-step passed: 0.000 → 0.005 → 0.010, then stable.");

            phase = "2D plots and diagnostics";
            click("Resume");
            await(() -> snapshot().time() >= 50);
            click("Pause");
            await(() -> label("Paused"));
            check(edt(() -> Arrays.stream(curves("timeSeries")).allMatch(curve -> curve.length() > 1000)
                    && curves("diagnostics")[0].length() > 1000), "Live sPlot curves");
            check(edt(() -> lyapunov().isShowing() && lyapunov().getText().contains("Averaged over")),
                    "Running Lyapunov estimate is visible");
            var panel = edt(() -> field(field(app, "diagnostics"), "fitPanel"));
            edt(() -> {
                ((JTextField) field(panel, "start")).setText("15");
                ((JTextField) field(panel, "end")).setText("30");
                return null;
            });
            click("Fit");
            check(edt(() -> {
                String result = ((JLabel) field(panel, "result")).getText();
                var estimate = (DivergenceFit) field(panel, "estimate");
                return result.contains("[15, 30]") && result.contains("Finite-time slope estimate")
                        && estimate.slope() > 0.5 && estimate.slope() < 1.1
                        && ((Curve) field(panel, "line")).length() == 2;
            }), "Selected interval, slope and two line endpoints");
            System.out.println("2D plots, interactive divergence fit and Lyapunov estimate passed.");
            phase = "diagnostics CSV export";
            verifyCsvExport();
            phase = "first 3D open";
            click("3D view");
            await(() -> field(app, "trajectories3D") != null);
            System.out.println("3D initialization returned");
            var view3d = edt(() -> (JInternalFrame) field(app, "trajectories3D"));
            var scene = edt(() -> (Panel3D) field(view3d, "scene"));
            var rotation = edt(() -> (Timer) field(view3d, "rotation"));
            check(scene.isGLAvailable(), "Working OpenGL context: " + scene.getGLInitError());
            await(() -> view3d.isShowing() && rotation.isRunning() && scene.getGLJPanel().isRealized());
            check(edt(() -> {
                var trail = (Deque<?>) field(view3d, "trail");
                return trail.size() > 1000 && ((ChaosSnapshot) trail.getLast()).time() >= 49;
            }), "3D backfilled the shared trajectory history");
            var autoRotate = edt(() -> (JCheckBox) field(view3d, "autoRotate"));
            edt(() -> {
                autoRotate.doClick(0);
                check(!rotation.isRunning(), "Disable auto rotation");
                // Capture once before closing to exercise the same GL paint path as the original stall check.
                var image = new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    app.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                autoRotate.doClick(0);
                return null;
            });
            for (int i = 1; i <= REOPEN_CYCLES; i++) {
                phase = "3D close/reopen cycle " + i;
                edt(() -> {
                    view3d.doDefaultCloseAction();
                    check(!view3d.isShowing() && !rotation.isRunning(), "Close stops rotation immediately");
                    return null;
                });
                click("3D view");
                await(() -> view3d.isShowing() && rotation.isRunning() && scene.getGLJPanel().isRealized());
            }
            System.out.println("3D auto-rotation and " + REOPEN_CYCLES + " close/reopen cycles passed.");

            phase = "reset and replay";
            click("Reset");
            await(() -> label("Ready"));
            checkReset(initial);
            check(edt(() -> ((Deque<?>) field(view3d, "trail")).size() == 1
                    && ((ChaosSnapshot) field(view3d, "current")).time() == 0), "3D resets with the 2D plots");
            check(edt(() -> lyapunov().getText().contains("warming up")), "Reset clears Lyapunov average");
            check(edt(() -> ((Curve) field(field(field(app, "diagnostics"), "fitPanel"), "line")).length() == 0),
                    "Reset clears selected interval fit");
            click("Single step");
            await(() -> label("Paused"));
            check(edt(ReleaseDesktopSmoke::snapshot).equals(first), "Replay after reset");

            click("Resume");
            await(() -> label("Running") && snapshot().step() > 10);
            var running = edt(ReleaseDesktopSmoke::simulation);
            click("Reset");
            await(() -> label("Ready"));
            checkReset(initial);
            check(edt(() -> lyapunov().getText().contains("warming up")), "Reset clears Lyapunov average");
            check(edt(() -> ((Curve) field(field(field(app, "diagnostics"), "fitPanel"), "line")).length() == 0),
                    "Reset clears selected interval fit");
            check(running.engine().awaitTermination(3000), "Reset stopped old worker");

            edt(() -> {
                var controls = field(app, "controls");
                ((JTextField) field(controls, "dt")).setText("0.01");
                return null;
            });
            click("Reset");
            await(() -> label("Ready"));
            click("Single step");
            await(() -> label("Paused"));
            check(edt(ReleaseDesktopSmoke::snapshot).time() == 0.01, "Reset applied edited dt");
            edt(() -> { checkbox(app).doClick(); return null; });
            click("Reset");
            await(() -> label("Ready") && !lyapunov().isVisible());
            System.out.println("Lyapunov mode passed: opt-in on reset, warm-up, estimate alongside fit, reset and opt-out.");
            System.out.println("Controls passed: resume, pause, reset from paused and running, deterministic replay, edited dt.");
            phase = "application shutdown";
            edt(() -> {
                app.dispatchEvent(new WindowEvent(app, WindowEvent.WINDOW_CLOSING));
                return null;
            });
        } catch (Throwable e) {
            System.err.println("Desktop smoke test failed during " + phase);
            e.printStackTrace();
            Runtime.getRuntime().halt(STARTUP.equals(phase) && e instanceof TimeoutException ? STARTUP_TIMEOUT : 1);
        }
    }
    private static void verifyCsvExport() throws Exception {
        Path selection = Path.of("diagnostics-smoke").toAbsolutePath();
        Path output = selection.resolveSibling("diagnostics-smoke.csv");
        edt(() -> {
            JButton export = button("Export CSV");
            check(export != null && export.isShowing() && export.isEnabled(), "Export CSV is available");
            SwingUtilities.invokeLater(() -> export.doClick(0));
            return null;
        });
        await(() -> visibleFileChooser() != null);
        edt(() -> {
            var chooser = visibleFileChooser();
            chooser.setSelectedFile(selection.toFile());
            chooser.approveSelection();
            return null;
        });
        await(() -> Files.isRegularFile(output));
        var rows = Files.readAllLines(output);
        check(rows.size() > 1000, "CSV includes retained samples");
        String[] columns = rows.get(0).split(",", -1);
        String[] values = rows.get(1).split(",", -1);
        check(columns.length == 19 && values.length == columns.length, "CSV columns and sample row");
        check(values[4].equals("10.0") && values[7].equals("0.005")
                        && values[9].equals("15.0") && values[10].equals("30.0")
                        && Double.parseDouble(values[11]) > 0.5
                        && values[13].equals("estimated") && Double.parseDouble(values[15]) > 0,
                "CSV includes active parameters, fit and Lyapunov metadata");
        System.out.println("Diagnostics CSV export passed with " + (rows.size() - 1) + " sample rows.");
    }

    private static JFileChooser visibleFileChooser() {
        return Arrays.stream(Window.getWindows())
                .filter(Window::isShowing)
                .map(ReleaseDesktopSmoke::fileChooser)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static JFileChooser fileChooser(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JFileChooser chooser) {
                return chooser;
            }
            if (component instanceof Container child) {
                JFileChooser chooser = fileChooser(child);
                if (chooser != null) {
                    return chooser;
                }
            }
        }
        return null;
    }

    private static JLabel lyapunov() throws Exception { return (JLabel) field(field(app, "diagnostics"), "lyapunov"); }
    private static JCheckBox checkbox(Container parent) {
        for (var child : parent.getComponents()) {
            if (child instanceof JCheckBox candidate && candidate.getText().equals("Estimate Lyapunov exponent"))
                return candidate;
            if (child instanceof Container group) {
                var match = checkbox(group);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static void checkReset(ChaosSnapshot initial) throws Exception {
        check(edt(() -> snapshot().equals(initial)
                && Arrays.stream(curves("timeSeries")).allMatch(curve -> curve.length() == 1)
                && curves("diagnostics")[0].length() == 1), "Reset cleared history and restored initial state");
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static ChaosSimulation simulation() throws Exception { return (ChaosSimulation) field(app, "simulation"); }
    private static ChaosSnapshot snapshot() throws Exception { return simulation().readSince(Long.MAX_VALUE).current(); }
    private static Curve[] curves(String name) throws Exception { return (Curve[]) field(field(app, name), "curves"); }
    private static boolean label(String text) throws Exception {
        return ((JLabel) field(field(app, "controls"), "status")).getText().equals(text);
    }
    private static JButton button(String text) { return button(app, text); }
    private static JButton button(Container parent, String text) {
        for (var child : parent.getComponents()) {
            if (child instanceof JButton candidate && text.equals(candidate.getText())) return candidate;
            if (child instanceof Container group) {
                var match = button(group, text);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static void click(String text) throws Exception {
        edt(() -> {
            var button = button(text);
            check(button != null && button.isShowing() && button.isEnabled(), text + " enabled and visible");
            button.doClick(0);
            return null;
        });
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        SwingUtilities.invokeLater(task);
        try {
            return task.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                if (thread.getName().startsWith("AWT-EventQueue") || thread.getName().contains("SwingWorker")) {
                    System.err.println(thread);
                    for (var frame : stack) {
                        System.err.println("  " + frame);
                    }
                }
            });
            var timeout = new TimeoutException("Swing event thread did not respond");
            timeout.initCause(error);
            throw timeout;
        }
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        do {
            if (edt(condition)) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new TimeoutException("Timed out waiting for desktop state");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
