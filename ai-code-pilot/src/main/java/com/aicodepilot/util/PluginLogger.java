package com.aicodepilot.util;

import com.aicodepilot.Activator;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging facade that bridges SLF4J and Eclipse's ILog.
 *
 * <p>SLF4J is used for application-level logging (shows in console view).
 * Eclipse's ILog is used for errors that should appear in the Error Log view.
 * Both channels are populated so users can find logs in either place.
 *
 * <p>Usage:
 * <pre>
 *   PluginLogger.info("Engine loaded.");
 *   PluginLogger.error("Something broke", exception);
 * </pre>
 */
public final class PluginLogger {

    private static final Logger log = LoggerFactory.getLogger("AICodePilot");

    private PluginLogger() {}

    public static void info(String message) {
        log.info(message);
    }

    public static void warn(String message) {
        log.warn(message);
    }

    public static void debug(String message) {
        log.debug(message);
    }

    public static void error(String message, Throwable t) {
        log.error(message, t);
        // Also write to Eclipse Error Log for visibility in the IDE
        ILog eclipseLog = getEclipseLog();
        if (eclipseLog != null) {
            eclipseLog.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, message, t));
        }
    }

    public static void error(String message) {
        log.error(message);
    }

    private static ILog getEclipseLog() {
        try {
            Activator plugin = Activator.getDefault();
            return plugin != null ? plugin.getLog() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
