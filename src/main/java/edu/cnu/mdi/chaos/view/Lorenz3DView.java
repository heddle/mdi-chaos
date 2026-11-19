package edu.cnu.mdi.chaos.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.jogamp.opengl.GLAutoDrawable;
import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.chaos.model.LorenzState;
import edu.cnu.mdi.chaos.sim.ChaosSimulation;
import edu.cnu.mdi.mdi3D.item3D.Item3D;
import edu.cnu.mdi.mdi3D.panel.Panel3D;
import edu.cnu.mdi.mdi3D.panel.Support3D;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.BaseView;

public final class Lorenz3DView extends BaseView {
    private static final Color PRIMARY = new Color(77, 223, 239);
    private static final Color PERTURBED = new Color(255, 164, 91);
    private final Deque<ChaosSnapshot> trail = new ArrayDeque<>();
    private final Panel3D scene;
    private final Orbits orbits;
    private final JCheckBox autoRotate = new JCheckBox("Auto-rotate", true);
    private final JLabel time = new JLabel();
    private final Timer rotation;
    private ChaosSnapshot current;
    private double centerZ;
    private double scale;

    public Lorenz3DView(LorenzParameters parameters) {
        super(PropertyUtils.TITLE, "Lorenz Trajectories (3D)", PropertyUtils.USECONTAINER, false,
                PropertyUtils.WIDTH, 550, PropertyUtils.HEIGHT, 480);
        scene = new Panel3D(-62, 0, -20, 0, 0, -3.6f, 0.05f, 0.086f, 0.133f, false);
        orbits = new Orbits(scene);
        scene.addItem(orbits);
        add(scene, BorderLayout.CENTER);

        var controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(autoRotate);
        if (scene.isGLAvailable()) {
            controls.add(new JLabel("Cyan: primary  ·  Orange: perturbed"));
            controls.add(time);
        }
        add(controls, BorderLayout.SOUTH);

        rotation = new Timer(50, event -> scene.rotateY(0.65f));
        autoRotate.setEnabled(scene.isGLAvailable());
        autoRotate.addActionListener(event -> updateRotation());
        addPropertyChangeListener(IS_ICON_PROPERTY, event -> {
            updateRotation();
            if (!isIcon()) {
                render();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent event) {
                updateRotation();
                render();
            }

            @Override
            public void componentHidden(ComponentEvent event) {
                updateRotation();
            }
        });
        clear(parameters);
    }

    public void clear(LorenzParameters parameters) {
        centerZ = Math.max(0, parameters.rho() - 1);
        scale = Math.max(29, Math.max(parameters.rho() + 5, 1.4 * Math.sqrt(parameters.beta() * parameters.rho()) + 8));
        trail.clear();
        current = null;
        orbits.coordinates = Coordinates.EMPTY;
        time.setText("");
        scene.softRefresh();
    }

    public void update(ChaosSimulation.Frame frame) {
        if (!scene.isGLAvailable()) {
            return;
        }
        boolean changed = !frame.current().equals(current);
        current = frame.current();
        for (ChaosSnapshot sample : frame.samples()) {
            // Initial backfill can include samples that the next shared refresh still has to deliver.
            if (!trail.isEmpty() && sample.step() <= trail.getLast().step()) {
                continue;
            }
            if (trail.size() == ChaosSimulation.HISTORY_CAPACITY) {
                trail.removeFirst();
            }
            trail.addLast(sample);
        }
        if (changed && isShowing() && !isIcon()) {
            render();
        }
    }

    private void render() {
        if (current == null || !scene.isGLAvailable()) {
            return;
        }
        float[] primary = new float[3 * (trail.size() + 1)];
        float[] perturbed = new float[primary.length];
        int offset = 0;
        for (ChaosSnapshot sample : trail) {
            write(primary, offset, sample.primary());
            write(perturbed, offset, sample.perturbed());
            offset += 3;
        }
        write(primary, offset, current.primary());
        write(perturbed, offset, current.perturbed());
        orbits.coordinates = new Coordinates(primary, perturbed);
        time.setText(String.format(Locale.ROOT, "t = %.3f", current.time()));
        scene.softRefresh();
    }

    private void write(float[] coordinates, int offset, LorenzState state) {
        coordinates[offset] = (float) (state.x() / scale);
        coordinates[offset + 1] = (float) (state.y() / scale);
        coordinates[offset + 2] = (float) ((state.z() - centerZ) / scale);
    }

    private void updateRotation() {
        if (isShowing() && !isIcon() && scene.isGLAvailable() && autoRotate.isSelected()) {
            rotation.start();
        } else {
            rotation.stop();
        }
    }

    @Override
    public void prepareForExit() {
        rotation.stop();
        if (scene.isGLAvailable()) {
            scene.getGLJPanel().destroy();
        }
        super.prepareForExit();
    }

    // Published vertex arrays belong to one render snapshot and are never mutated after publication.
    private record Coordinates(float[] primary, float[] perturbed) {
        private static final Coordinates EMPTY = new Coordinates(new float[0], new float[0]);
    }

    private static final class Orbits extends Item3D {
        private volatile Coordinates coordinates = Coordinates.EMPTY;

        private Orbits(Panel3D scene) {
            super(scene);
        }

        @Override
        public void draw(GLAutoDrawable drawable) {
            Coordinates frame = coordinates;
            int points = frame.primary().length / 3;
            Support3D.drawPolyLine(drawable, frame.primary(), points, PRIMARY, 1.5f);
            Support3D.drawPolyLine(drawable, frame.perturbed(), points, PERTURBED, 1f);
        }
    }
}
