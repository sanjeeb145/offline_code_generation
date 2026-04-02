package com.aicodepilot;

import com.aicodepilot.engine.AIEngineManager;
import com.aicodepilot.engine.embedding.EmbeddingService;
import com.aicodepilot.util.PluginLogger;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * AI Code Pilot OSGi Bundle Activator.
 *
 * <p>Manages the plugin lifecycle:
 * <ul>
 *   <li>{@link #start} — creates background executor, schedules AI engine warm-up</li>
 *   <li>{@link #stop}  — graceful shutdown of engine and thread pool</li>
 * </ul>
 *
 * <p>The AI engine warms up in a low-priority background Job so Eclipse
 * startup is not slowed down.
 */
public class Activator extends AbstractUIPlugin implements IStartup {

    /** OSGi bundle symbolic name — must match MANIFEST.MF. */
    public static final String PLUGIN_ID = "com.aicodepilot";

    private static final String MODEL_DIR   = ".aicodepilot/models";
    private static final int    WORKERS     = 2;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static Activator instance;

    // ── Services ──────────────────────────────────────────────────────────────
    private AIEngineManager  aiEngineManager;
    private EmbeddingService embeddingService;
    private ExecutorService  backgroundExecutor;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        PluginLogger.info("AI Code Pilot starting (JDK 21)...");

        ensureModelDirectory();

        // Virtual threads (JDK 21) — lightweight, low overhead
        backgroundExecutor = Executors.newFixedThreadPool(WORKERS, r -> {
            Thread t = Thread.ofVirtual()
                    .name("AICodePilot-Worker")
                    .unstarted(r);
            return t;
        });

        scheduleEngineInit();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        PluginLogger.info("AI Code Pilot shutting down...");

        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
            try {
                if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    backgroundExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                backgroundExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (aiEngineManager  != null) aiEngineManager.shutdown();
        if (embeddingService != null) embeddingService.shutdown();

        instance = null;
        super.stop(context);
        PluginLogger.info("AI Code Pilot stopped.");
    }

    // ── IStartup ──────────────────────────────────────────────────────────────

    @Override
    public void earlyStartup() {
        // Engine init is already scheduled in start() — nothing extra needed.
        PluginLogger.info("AI Code Pilot early startup complete.");
    }

    // ── Init helpers ─────────────────────────────────────────────────────────

    private void scheduleEngineInit() {
        Job job = Job.create("Initializing AI Code Pilot Engine", monitor -> {
            monitor.beginTask("Loading AI engine...", IStatus.OK);
            try {
                aiEngineManager = new AIEngineManager(getModelDirectory());
                aiEngineManager.initialize(monitor);

                embeddingService = new EmbeddingService(aiEngineManager);
                embeddingService.initialize();

                PluginLogger.info("AI engine ready: " + aiEngineManager.getEngineType());
            } catch (Exception e) {
                PluginLogger.error("AI engine initialization failed", e);
                return new Status(IStatus.WARNING, PLUGIN_ID,
                        "AI engine failed to load: " + e.getMessage(), e);
            }
            monitor.done();
            return Status.OK_STATUS;
        });
        job.setPriority(Job.DECORATE); // Lowest priority — runs in background
        job.setSystem(true);           // No progress dialog
        job.schedule(1500L);           // 1.5s delay — let Eclipse finish startup
    }

    private void ensureModelDirectory() {
        try {
            Path dir = getModelDirectory();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                PluginLogger.info("Created model directory: " + dir);
            }
        } catch (IOException e) {
            PluginLogger.error("Cannot create model directory", e);
        }
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public static Activator getDefault()           { return instance; }
    public AIEngineManager  getAIEngineManager()   { return aiEngineManager; }
    public EmbeddingService getEmbeddingService()  { return embeddingService; }
    public ExecutorService  getBackgroundExecutor(){ return backgroundExecutor; }

    public boolean isEngineReady() {
        return aiEngineManager != null && aiEngineManager.isReady();
    }

    public Path getModelDirectory() {
        String pref = getPreferenceStore().getString("model.directory");
        if (pref != null && !pref.isBlank()) return Paths.get(pref);
        return Paths.get(System.getProperty("user.home"), MODEL_DIR);
    }

    public static void logError(String msg, Throwable t) {
        if (instance != null) {
            instance.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, msg, t));
        }
    }
}
