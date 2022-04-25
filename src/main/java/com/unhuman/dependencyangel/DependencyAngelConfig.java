package com.unhuman.dependencyangel;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.HashMap;
import java.util.Map;

public class DependencyAngelConfig {
    public enum Mode { SetupDependencyManagement, Process }

    private String directory;
    private Map<String, String> environmentVars;
    private boolean skipPrompts;
    private boolean cleanOnly;
    private boolean noClean;
    private Mode mode;

    public DependencyAngelConfig(String[] args) {
        this.directory = null;
        this.environmentVars = new HashMap<>();
        this.skipPrompts = false;
        this.cleanOnly = false;
        this.noClean = false;
        this.mode = Mode.Process;

        ArgumentParser parser = ArgumentParsers.newFor(DependencyAngel.class.getSimpleName()).build()
                .defaultHelp(true)
                .description("Resolve conflicting dependencies (exclusions).  " +
                        "This is a destructive process.  Have backups!");
        parser.addArgument("-c", "--cleanOnly")
                .type(Boolean.class)
                .required(false)
                .setDefault(false)
                .help("Perform clean up only (cannot be used with noClean).  For process mode.");
        parser.addArgument("-e", "--env")
                .type(String.class)
                .required(false)
                .help("Specify environment variables (comma separated, k=v pairs).  For process mode.");
        parser.addArgument("-m", "--mode")
                .type(Mode.class)
                .required(false)
                .setDefault(Mode.Process)
                .help("Mode how to operate (SetupDependencyManagement or Process).");
        parser.addArgument("-n", "--noClean")
                .type(Boolean.class)
                .required(false)
                .setDefault(false)
                .help("Skips clean step (cannot be use with cleanOnly).");
        parser.addArgument("-s", "--skipPrompts")
                .type(Boolean.class)
                .setDefault(false)
                .help("Specify to skip any prompts.");
        parser.addArgument("directory")
                .type(String.class)
                .required(true)
                .help("Directory of project to modify.");
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);

            directory = ns.getString("directory");
            environmentVars.putAll(getEnvParameterMap(ns.getString("env")));
            // Add JAVA_HOME if it doesn't exist in the environment
            if (!environmentVars.containsKey("JAVA_HOME") && System.getenv("JAVA_HOME") != null) {
                environmentVars.put("JAVA_HOME", System.getenv("JAVA_HOME"));
            }
            cleanOnly = ns.getBoolean("cleanOnly");
            skipPrompts = ns.getBoolean("skipPrompts");
            noClean = ns.getBoolean("noClean");
            mode = ns.get("mode");
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        if (cleanOnly && noClean) {
            throw new RuntimeException("cleanOnly and noClean cannot be provided together");
        }
    }


    public String getDirectory() {
        return directory;
    }

    public Map<String, String> getEnvironmentVars() {
        return environmentVars;
    }

    public boolean isSkipPrompts() {
        return skipPrompts;
    }

    public boolean isCleanOnly() {
        return cleanOnly;
    }

    public boolean isNoClean() {
        return noClean;
    }

    public Mode getMode() {
        return mode;
    }

    protected static Map<String, String> getEnvParameterMap(String env) {
        Map<String, String> environmentVars = new HashMap<>();
        if (env != null) {
            for (String envItem: env.split(",")) {
                String[] kv = envItem.split("=");
                if (kv.length != 2) {
                    throw new RuntimeException("Invalid environment (comma separated = split pairs): " + env);
                }
                environmentVars.put(kv[0], kv[1]);
            }
        }
        return environmentVars;
    }
}
