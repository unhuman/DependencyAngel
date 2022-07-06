package com.unhuman.dependencyangel;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.util.HashMap;
import java.util.Map;

public class DependencyAngelConfig extends StorableAngelConfigData {
    public enum Mode { All, SetupOnly, Continue, ProcessOnly, ProcessSingleStep, ExclusionReduction }

    private String directory;
    private Map<String, String> environmentVars;
    private Mode mode;
    private boolean skipPrompts;
    private boolean displayExecutionOutput;

    public DependencyAngelConfig(String[] args) {
        super();
        this.directory = null;
        this.environmentVars = new HashMap<>();
        this.skipPrompts = false;

        ArgumentParser parser = ArgumentParsers.newFor(DependencyAngel.class.getSimpleName()).build()
                .defaultHelp(true)
                .description("Resolve conflicting dependencies (exclusions)." +
                        "\nThis is a destructive process.  Have backups!" +
                        "\nSee: https://github.com/unhuman/DependencyAngel");
        parser.addArgument("-b", "--banned")
                .type(String.class)
                .metavar("<groupId:artifactId,...>")
                .required(false)
                .help("Banned dependencies.  Processing preserves exclusions.");
        parser.addArgument("-d", "--displayExecutionOutput")
                .type(Boolean.class)
                .required(false)
                .action(Arguments.storeTrue())
                .help("Display external process execution output.");
        parser.addArgument("-e", "--env")
                .type(String.class)
                .metavar("<key:value,...>")
                .required(false)
                .help("Specify environment variables.");
        parser.addArgument("-m", "--mode")
                .type(Mode.class)
                .required(false)
                .setDefault(Mode.All)
                .help("Mode how to operate (All, SetupOnly, Continue, " +
                        "ProcessOnly, ProcessSingleStep, or ExclusionReduction).");
        parser.addArgument("-n", "--nonSemanticVersioning")
                .type(String.class)
                .metavar("<groupId:artifactId,...>")
                .required(false)
                .help("Non-semantic versioning known for this component.");
        parser.addArgument("-p", "--preserveExclusions")
                .type(String.class)
                .metavar("<groupId:artifactId,...>")
                .required(false)
                .help("Existing exclusions to preserve.");
        parser.addArgument("-s", "--skipPrompts")
                .type(Boolean.class)
                .action(Arguments.storeTrue())
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
            mode = ns.get("mode");
            skipPrompts = ns.getBoolean("skipPrompts");
            displayExecutionOutput = ns.get("displayExecutionOutput");

            super.setup(ns, directory);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

    public String getDirectory() {
        return directory;
    }

    public Map<String, String> getEnvironmentVars() {
        return environmentVars;
    }

    public boolean performSetup() {
        return Mode.All.equals(mode) || Mode.SetupOnly.equals(mode);
    }

    public boolean performProcess() {
        return Mode.All.equals(mode) || Mode.Continue.equals(mode) ||
                Mode.ProcessOnly.equals(mode) || Mode.ProcessSingleStep.equals(mode);
    }

    public boolean performProcessSingleStep() {
        return Mode.ProcessSingleStep.equals(mode);
    }

    public boolean performExclusionReduction() {
        return Mode.All.equals(mode) || Mode.Continue.equals(mode) || Mode.ExclusionReduction.equals(mode);
    }

    public boolean isSkipPrompts() {
        return skipPrompts;
    }

    public boolean isDisplayExecutionOutput() {
        return displayExecutionOutput;
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
