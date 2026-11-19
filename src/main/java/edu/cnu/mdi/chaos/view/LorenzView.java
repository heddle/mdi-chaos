package edu.cnu.mdi.chaos.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.chaos.model.LorenzState;
import edu.cnu.mdi.chaos.sim.ChaosSimulation;
import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.graphics.toolbar.ToolBits;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.AbstractViewInfo;
import edu.cnu.mdi.view.BaseView;

public final class LorenzView extends BaseView {
    private static final Color BACKGROUND = new Color(13, 22, 34);
    private static final Color GRID = new Color(38, 56, 73);
    private static final Color LABEL = new Color(170, 190, 208);
    private static final Color PRIMARY = new Color(77, 223, 239);
    private static final Color PERTURBED = new Color(255, 164, 91);
    private final Deque<ChaosSnapshot> trail = new ArrayDeque<>();
    private ChaosSnapshot current;

    public LorenzView() {
        super(PropertyUtils.TITLE, "Lorenz Trajectories",
                PropertyUtils.WIDTH, 560, PropertyUtils.HEIGHT, 560,
                PropertyUtils.WORLDSYSTEM, bounds(LorenzParameters.DEFAULT),
                PropertyUtils.BACKGROUND, BACKGROUND, PropertyUtils.ADDFEEDBACK, false,
                PropertyUtils.TOOLBARBITS, ToolBits.NAVIGATIONTOOLS | ToolBits.RESETZOOM | ToolBits.CAMERA | ToolBits.INFO,
                PropertyUtils.VISIBLE, true);
        getIContainer().getComponent().setBackground(BACKGROUND);
        getIContainer().setAfterDraw(this::draw);
    }

    @Override
    public AbstractViewInfo getViewInfo() {
        return new AbstractViewInfo() {
            @Override
            public String getTitle() {
                return "Lorenz Trajectories";
            }

            @Override
            public String getPurpose() {
                return "Compare two Lorenz systems with the same parameters and a small initial difference in x. "
                        + "Their x–z projections overlap at first and separate as the difference grows.";
            }

            @Override
            public String getTechnicalNotes() {
                return "Both systems advance in lockstep with fixed-step fourth-order Runge–Kutta. "
                        + "The time series and finite-time divergence plots share the same simulation clock.";
            }
        };
    }

    public void clear(LorenzParameters parameters) {
        current = null;
        trail.clear();
        getIContainer().resetWorldSystem(bounds(parameters));
        refresh();
    }

    public void update(ChaosSimulation.Frame frame) {
        current = frame.current();
        for (ChaosSnapshot sample : frame.samples()) {
            if (trail.size() == ChaosSimulation.HISTORY_CAPACITY) {
                trail.removeFirst();
            }
            trail.addLast(sample);
        }
        refresh();
    }

    private static Rectangle2D.Double bounds(LorenzParameters p) {
        double halfWidth = Math.max(29, 1.4 * Math.sqrt(p.beta() * p.rho()) + 8);
        return new Rectangle2D.Double(-halfWidth, -6, 2 * halfWidth, Math.max(62, 2 * p.rho() + 6));
    }

    private void draw(Graphics2D graphics, IContainer container) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawGrid(g, container);
            if (current != null) {
                drawOrbit(g, container, false, PRIMARY);
                drawOrbit(g, container, true, PERTURBED);
            }
            drawLegend(g, container.getComponent().getWidth());
        } finally {
            g.dispose();
        }
    }

    private void drawLegend(Graphics2D g, int width) {
        g.setColor(BACKGROUND);
        g.fillRoundRect(9, 8, 205, 30, 8, 8);
        g.setColor(PRIMARY);
        g.fillRect(18, 22, 14, 2);
        g.drawString("Primary", 39, 27);
        g.setColor(PERTURBED);
        g.fillRect(111, 22, 14, 2);
        g.drawString("Perturbed", 132, 27);
        if (current != null) {
            String time = String.format(Locale.ROOT, "t = %.3f", current.time());
            int textWidth = g.getFontMetrics().stringWidth(time);
            g.setColor(BACKGROUND);
            g.fillRoundRect(width - textWidth - 25, 8, textWidth + 17, 30, 8, 8);
            g.setColor(LABEL);
            g.drawString(time, width - textWidth - 16, 27);
        }
    }

    private static void drawGrid(Graphics2D g, IContainer container) {
        var world = container.getWorldSystem();
        int width = container.getComponent().getWidth();
        int height = container.getComponent().getHeight();
        var point = new Point();
        double xSpacing = gridSpacing(world.width);
        double zSpacing = gridSpacing(world.height);
        for (int i = 0; i < 12; i++) {
            double x = (Math.ceil(world.getMinX() / xSpacing) + i) * xSpacing;
            if (x > world.getMaxX()) {
                break;
            }
            container.worldToLocal(point, x, 0);
            g.setColor(GRID);
            g.drawLine(point.x, 42, point.x, height - 27);
            g.setColor(LABEL);
            g.drawString(formatTick(x), point.x + 3, height - 10);
        }
        for (int i = 0; i < 12; i++) {
            double z = (Math.ceil(world.getMinY() / zSpacing) + i) * zSpacing;
            if (z > world.getMaxY()) {
                break;
            }
            container.worldToLocal(point, 0, z);
            if (point.y > 48 && point.y < height - 26) {
                g.setColor(GRID);
                g.drawLine(27, point.y, width - 12, point.y);
                g.setColor(LABEL);
                g.drawString(formatTick(z), 6, point.y + 4);
            }
        }
        g.setColor(LABEL);
        g.drawString("x", width - 16, height - 10);
        g.drawString("z", 8, 48);
    }

    private static double gridSpacing(double span) {
        double target = span / 6;
        double unit = Math.pow(10, Math.floor(Math.log10(target)));
        double scaled = target / unit;
        if (scaled >= 5) {
            return 5 * unit;
        }
        return scaled >= 2 ? 2 * unit : unit;
    }

    private static String formatTick(double value) {
        return value == (long) value ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2g", value);
    }

    private void drawOrbit(Graphics2D g, IContainer container, boolean perturbed, Color color) {
        var path = new Path2D.Double();
        var point = new Point();
        boolean started = false;
        for (ChaosSnapshot sample : trail) {
            LorenzState state = perturbed ? sample.perturbed() : sample.primary();
            container.worldToLocal(point, state.x(), state.z());
            if (!started) {
                path.moveTo(point.x, point.y);
                started = true;
            } else {
                path.lineTo(point.x, point.y);
            }
        }
        LorenzState head = perturbed ? current.perturbed() : current.primary();
        container.worldToLocal(point, head.x(), head.z());
        if (started) {
            path.lineTo(point.x, point.y);
        }
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 155));
        g.setStroke(new BasicStroke(perturbed ? 1.0f : 1.5f));
        g.draw(path);
        g.setColor(color);
        if (perturbed) {
            g.drawOval(point.x - 5, point.y - 5, 10, 10);
        } else {
            g.fillOval(point.x - 3, point.y - 3, 6, 6);
        }
    }
}
