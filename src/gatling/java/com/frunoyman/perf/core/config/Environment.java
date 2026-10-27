package com.frunoyman.perf.core.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

/**
 * The target a run takes — the {@code -Denv} block ({@code local} by default) of {@code application.conf}. Each service reads its
 * own part ({@link #block}) into a settings record. HOCON is Typesafe Config, already on Gatling's classpath; any value is
 * overridable with {@code -D<env>.<path>=…}, and a secret would come from the environment as {@code ${?NAME}} — grpcbin needs none.
 */
public final class Environment {

    private Environment() {
    }

    public static String name() {
        return System.getProperty("env", "local");
    }

    /** {@code path} of the current environment's block: {@code grpcbin} … */
    public static Config block(String path) {
        Config all = ConfigFactory.load();
        if (!all.hasPath(name())) {
            throw new IllegalStateException("No environment '" + name() + "' in application.conf");
        }
        return all.getConfig(name()).getConfig(path);
    }

    /** A value that must be set. */
    public static String required(Config config, String path) {
        try {
            return config.getString(path);
        } catch (ConfigException.Missing e) {
            throw new IllegalStateException("No " + path + " in " + name() + " (" + config.origin().description() + ")", e);
        }
    }
}
