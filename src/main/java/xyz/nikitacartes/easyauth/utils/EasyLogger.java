package xyz.nikitacartes.easyauth.utils;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static xyz.nikitacartes.easyauth.EasyAuth.config;
import static xyz.nikitacartes.easyauth.EasyAuth.extendedConfig;

public class EasyLogger {
    public static final Logger LOGGER = LoggerFactory.getLogger("EasyAuth");

    static void log(Level level, String message) {
        LOGGER.atLevel(level).log(("[EasyAuth]: " + message));
    }

    static void log(Level level, String message, Throwable e) {
        LOGGER.atLevel(level).log("[EasyAuth]: {}\n{}", message, ExceptionUtils.getStackTrace(e));
    }

    public static void LogInfo(String message) {
        log(Level.INFO, message);
    }

    public static void LogInfo(String message, Throwable e) {
        log(Level.INFO, message, e);
    }

    public static void LogWarn(String message) {
        log(Level.WARN, message);
    }

    public static void LogWarn(String message, Throwable e) {
        log(Level.WARN, message, e);
    }

    public static void LogDebug(String message) {
        if (extendedConfig != null && config.debug) {
            log(Level.INFO, "[DEBUG]: " + message);
        }
    }

    public static void LogDebug(String message, Throwable e) {
        if (config != null && config.debug) {
            log(Level.INFO, "[DEBUG]: " + message, e);
        }
    }

    public static void LogLogin(String message) {
        log(extendedConfig.logPlayerLogin ? Level.INFO : Level.DEBUG, "[LOGIN]: " + message);
    }

    public static void LogRegister(String message) {
        log(extendedConfig.logPlayerRegistration ? Level.INFO : Level.DEBUG, "[REGISTER]: " + message);
    }

    public static void LogError(String message) {
        log(Level.ERROR, message);
    }

    public static void LogError(String message, Throwable e) {
        log(Level.ERROR, message, e);
    }
}