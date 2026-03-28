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
 * AI Code Pilot Plugin Activator.
 *
 * <p>Manages the OSGi bundle lifecycle. Initializes the local AI engine,
 * embedding service, and background workers on startup. Performs clean
 * shutdown on stop.
 *
 * <p>This class is also an {@link IStartup} implementation, causing the
 * plugin to load eagerly when Eclipse starts — necessary so the AI engine
 * warms up in the background before the first user action.
 */
public class Activator extends AbstractUIPlugin implements IStartup {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** OSGi bundle symbolic name — must match MANIFEST.MF. */
    public static final String PLUGIN_ID = "com.aicodepilot";

    /** Default model directory relative to Eclipse workspace. */
    private static final String MODEL_DIR_NAME = ".aicodepilot/models";

    /** Number of background worker threads for AI inference. */
    private static final int WORKER_THREAD_COUNT = 2;

    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    private static Activator plugin;

    // -----------------------------------------------------------------------
    // Core services (initialized once, shared across the plugin)
    // -----------------------------------------------------------------------

    private AIEngineManager aiEngineManager;
    private EmbeddingService embeddingService;
    private ExecutorService backgroundExecutor;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        PluginLogger.info("AI Code Pilot starting up...");

        // Ensure model directory exists
        ensureModelDirectory();

        // Initialize background thread pool for inference tasks
        backgroundExecutor = Executors.newFixedThreadPool(
                WORKER_THREAD_COUNT,
                r -> {
                    Thread t = new Thread(r, "AICodePilot-Worker");
                    t.setDaemon(true); // Do not block Eclipse shutdown
                    t.setPriority(Thread.NORM_PRIORITY - 1); // Below UI priority
                    return t;
                }
        );

        // Lazy-initialize AI engine in background via Eclipse Job API
        // This avoids slowing down Eclipse startup
        scheduleEngineInitialization();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        PluginLogger.info("AI Code Pilot shutting down...");

        // Shutdown background workers gracefully
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

        // Shutdown AI engine (releases native resources, model handles)
        if (aiEngineManager != null) {
            aiEngineManager.shutdown();
        }

        // Shutdown embedding service
        if (embeddingService != null) {
            embeddingService.shutdown();
        }

        plugin = null;
        super.stop(context);
        PluginLogger.info("AI Code Pilot shutdown complete.");
    }

    // -----------------------------------------------------------------------
    // IStartup
    // -----------------------------------------------------------------------

    /**
     * Called by Eclipse on workbench startup (IStartup contract).
     * The actual heavy work is done asynchronously to keep startup fast.
     */
    @Override
    public void earlyStartup() {
        // earlyStartup is invoked after the workbench is fully initialized.
        // The engine initialization job was already scheduled in start(),
        // so nothing additional is needed here. This hook exists so the
        // plugin class is listed as a startup contributor in plugin.xml.
        PluginLogger.info("AI Code Pilot early startup complete.");
    }

    // -----------------------------------------------------------------------
    // Initialization helpers
    // -----------------------------------------------------------------------

    /**
     * Schedules AI engine warm-up as a low-priority Eclipse background Job.
     * The Job API integrates with Eclipse progress reporting and cancellation.
     */
    private void scheduleEngineInitialization() {
        Job initJob = Job.create("Initializing AI Code Pilot Engine", monitor -> {
            monitor.beginTask("Loading AI model...", IStatus.OK);
            try {
                // Initialize AI Engine Manager (detects model, loads weights)
                aiEngineManager = new AIEngineManager(getModelDirectory());
                aiEngineManager.initialize(monitor);

                // Initialize Embedding Service (used for context-aware suggestions)
                embeddingService = new EmbeddingService(aiEngineManager);
                embeddingService.initialize();

                PluginLogger.info("AI engine initialized successfully. Engine: "
                        + aiEngineManager.getEngineType());
            } catch (Exception e) {
                PluginLogger.error("Failed to initialize AI engine", e);
                return new Status(IStatus.ERROR, PLUGIN_ID,
                        "AI engine initialization failed: " + e.getMessage(), e);
            }
            monitor.done();
            return Status.OK_STATUS;
        });

        initJob.setPriority(Job.DECORATE); // Lowest priority — background
        initJob.setSystem(true);           // No progress dialog shown
        initJob.schedule(2000L);           // Delay 2s to let Eclipse fully start
    }

    /**
     * Creates the model directory inside the Eclipse workspace if absent.
     */
    private void ensureModelDirectory() {
        try {
            Path modelDir = getModelDirectory();
            if (!Files.exists(modelDir)) {
                Files.createDirectories(modelDir);
                PluginLogger.info("Created model directory: " + modelDir);
            }
        } catch (IOException e) {
            PluginLogger.error("Cannot create model directory", e);
        }
    }

    // -----------------------------------------------------------------------
    // Public accessors (used by handlers, views, and services)
    // -----------------------------------------------------------------------

    /**
     * Returns the shared plugin instance.
     */
    public static Activator getDefault() {
        return plugin;
    }

    /**
     * Returns the initialized AI engine manager, or {@code null} if still loading.
     * Callers should handle null gracefully (e.g., show "Engine loading..." in UI).
     */
    public AIEngineManager getAIEngineManager() {
        return aiEngineManager;
    }

    /**
     * Returns the embedding service used for RAG / context retrieval.
     */
    public EmbeddingService getEmbeddingService() {
        return embeddingService;
    }

    /**
     * Returns the shared background executor for AI inference tasks.
     * Handlers should submit inference tasks here rather than blocking the UI thread.
     */
    public ExecutorService getBackgroundExecutor() {
        return backgroundExecutor;
    }

    /**
     * Returns the absolute path to the AI model directory.
     * Defaults to {@code <workspace>/.aicodepilot/models}.
     */
    public Path getModelDirectory() {
        // Prefer preference value, fall back to workspace default
        String workspacePath = getPreferenceStore()
                .getString("model.directory");
        if (workspacePath == null || workspacePath.isBlank()) {
            workspacePath = System.getProperty("user.home");
        }
        return Paths.get(workspacePath, MODEL_DIR_NAME);
    }

    /**
     * Utility: log an error to the Eclipse error log.
     */
    public static void logError(String message, Throwable t) {
        if (plugin != null) {
            plugin.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, t));
        }
    }

    /**
     * Checks whether the AI engine has finished initializing.
     */
    public boolean isEngineReady() {
        return aiEngineManager != null && aiEngineManager.isReady();
    }
}
