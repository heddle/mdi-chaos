# MDI Chaos Lab

MDI Chaos Lab is a standalone Java 17 desktop simulation built on MDI 1.2.4. It advances two Lorenz systems with the same equations and parameters and a small difference in their starting positions. The desktop shows both trajectories in the x–z plane, the primary trajectory’s three coordinates on one sPlot chart, and a separate sPlot chart for divergence. An optional 3D view shows the same trajectories in full phase space.

![MDI Chaos Lab paused at simulation time 50, showing the 3D Lorenz trajectories alongside the 2D projection, time series, divergence fit, and running Lyapunov estimate](docs/desktop-screenshot.png)

## Run the prebuilt download

[Download the v1.0.2 runnable ZIP](https://github.com/heddle/mdi-chaos/releases/download/v1.0.2/mdi-chaos-1.0.2-runnable.zip) · [Release notes](https://github.com/heddle/mdi-chaos/releases/tag/v1.0.2).

With Java 17 or newer installed, extract the entire ZIP, open a terminal in the extracted folder, and run:

```sh
java -jar mdi-chaos.jar
```

Keep the bundled `lib` folder next to the JAR. No Maven build is required.

## Build from source

With Java 17 or newer and Maven, run the following from the project directory. Launching the application requires a graphical desktop. The optional 3D window uses MDI-3D 1.0.3 and JOGL, and needs OpenGL support. The application opens in 2D and initializes OpenGL when the 3D window is first requested. The tests run headlessly.

```sh
mvn clean verify
mvn exec:java
```

The entry point is `edu.cnu.mdi.chaos.app.ChaosApp`. The build uses the published `io.github.heddle:mdi:1.2.4` dependency.

## Build a runnable release ZIP

Pushing a version tag such as `v1.0.1` runs the Maven tests and builds the
runnable ZIP with Java 17 in GitHub Actions. Set the version in `pom.xml` to
match the tag before pushing it. If the tests and packaging pass, the workflow
publishes a GitHub release with generated notes and attaches the ZIP. The ZIP
is also available in the workflow run's **Artifacts** section.

To reproduce the build locally with Maven, a JDK, and Python 3:

```sh
bash scripts/build-runnable.sh
```

The ZIP is written to `target/`.

## Explore the simulation

- **Start** begins from the initial conditions. **Pause** stops at the next worker batch boundary. **Resume** continues the same run.
- **Single step** advances both systems by one integration step from Ready or Paused and leaves the simulation paused.
- **Reset** validates and applies the parameter fields, clears every view’s history, and restores the initial conditions at time zero. Start or single-step to continue.
- **Presets** fill the fields for the classic chaotic regime, a system that approaches the origin (Fixed point), or one that approaches a nonzero equilibrium (Stable lobe). Reset applies the selected values.
- **Fit a divergence interval** by entering **Fit start** and **Fit end** in Chaos Diagnostics and clicking **Fit**. The line is a least-squares fit to the recorded samples within those bounds. The view reports the interval, sample count, and finite-time slope estimate. Pause to inspect the curve, then choose a rising segment before saturation. **Clear fit** removes the line. Reset also clears it.
- **Estimate the largest Lyapunov exponent** by checking **Estimate Lyapunov exponent** and clicking **Reset**. The estimate appears in Chaos Diagnostics after warm-up and updates as complete intervals accumulate. Clear the checkbox and reset to disable it.
- **3D view** opens a movable MDI window showing the same trajectories in (x, y, z). Turn off **Auto-rotate** to hold the orientation. Drag to rotate manually and use the mouse wheel to zoom. Closing the 3D window leaves the simulation running. Reopen it to see the latest trail.
- **Capture a trajectory image** with the camera in the Lorenz Trajectories toolbar. For the classic butterfly, run until the time display reaches about 65–85, then pause. The same toolbar provides navigation and view help.

## Scientific model

The equations are `dx/dt = σ(y − x)`, `dy/dt = x(ρ − z) − y`, and `dz/dt = xy − βz`. Both trajectories use classical fourth-order Runge–Kutta (RK4) in double precision. The primary starts at (1, 1, 1) and the perturbed trajectory starts at (1 + δ₀, 1, 1). The simulation clock is the step index multiplied by the fixed integration step. Wall-clock pacing controls animation speed.

| Parameter | Default | Accepted values |
| --- | ---: | --- |
| σ | 10 | 0.01–100 |
| ρ | 28 | 0–100 |
| β | 8/3 | 0.01–100 |
| dt | 0.005 | 0.0001–0.01 |
| δ₀ | 10⁻⁸ | 0 or 10⁻¹⁵–0.1 |

The diagnostic plots the natural log of the instantaneous Euclidean separation divided by δ₀. Locally exponential growth can appear as a roughly straight segment, with fluctuations over a finite interval. Once the trajectories are separated at the scale of the attractor, the separation stays bounded and the log curve can flatten or fluctuate. A fit across that saturated portion would misrepresent the growth rate. The selected-interval slope is an illustrative finite-time estimate. Undefined log values are omitted. For δ₀ = 0 the two trajectories coincide and the diagnostic is undefined. Very small positive δ₀ values also expose floating-point rounding in the actual initial displacement. Numerical overflow stops the run and displays an error.

### Optional largest Lyapunov exponent estimate

A third trajectory starts approximately 10⁻⁷ away from the primary and uses the same time step and model parameters. Every 0.5 simulation time units (rounded to the nearest whole integration step), the estimator records the natural log of the separation growth, then rescales the separation back to 10⁻⁷ along its current direction. It uses the actual floating-point starting distance for each interval. After at least 10 units of warm-up, the running estimate divides the sum of recorded log growth by the total duration of the completed measurement intervals.

This is a finite-perturbation numerical estimate whose accuracy depends on run length, step size, and numerical resolution. The classic preset has a [reference largest exponent of about 0.906](https://sprott.physics.wisc.edu/chaos/lespec.htm) per simulation time unit. The estimate uses its own perturbation, independent of δ₀, so the displayed trajectories and interactive interval fit retain their original behavior.

## Execution and presentation

- `model` holds the headless RK4 integrator, validated parameters, immutable state and snapshot records, and a bounded ring. Continuous runs record a sample roughly every 0.02 simulated time units. Single steps are always recorded.
- `sim` adapts the model to MDI’s `SimulationEngine`. The worker alone mutates the model. It paces continuous integration in batches and MDI coalesces refresh events at roughly 25 Hz. Each EDT refresh reads the exact current state and copies only newly retained sample references under a short lock. The returned list and its samples are immutable.
- `app` and `view` coordinate controls, MDI drawing and sPlot on the EDT. Each history holds at most 6,000 samples. sPlot trims in blocks to amortize rebuilding its curve arrays. The oldest part of a long run eventually rolls off the display. Reset cancels the previous worker, clears the views, creates a new engine, and ignores callbacks from the retired engine.

MDI 1.2.4 exposes run, resume and pause, but no step-once transition. The app arms a one-shot flag while the engine is Ready or Paused, then requests run. The worker consumes that flag, advances both trajectories once, and requests pause before returning from its MDI step callback. MDI checks the pause flag on the same worker before invoking the callback again. The EDT keeps the run, pause and step buttons disabled until MDI confirms Paused. Reset can retire the engine while a command is pending.

The thirteen automated tests cover fourth-order convergence over a short interval, replay, reset, time, perturbation and divergence, bounded history, invalid inputs, selected-interval regression, Lyapunov estimation, and single-step behavior through the real MDI engine.
