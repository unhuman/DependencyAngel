package com.unhuman.dependencyresolver;

import com.unhuman.dependencyresolver.pom.PomManipulator;
import com.unhuman.dependencyresolver.tgf.TgfData;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DependencyResolver {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private String directory;
    private Map<String, String> environmentVars;
    private boolean skipPrompts;

    protected DependencyResolver(String directory, Map<String, String> environmentVars, boolean skipPrompts) {
        this.directory = directory;
        this.environmentVars = environmentVars;
        this.skipPrompts = skipPrompts;
    }

    protected void process() {
        File directoryFile = new File(directory).getAbsoluteFile();
        if (!directoryFile.isDirectory()) {
            throw new RuntimeException(String.format("Directory: %s is not a directory", directory));
        }

        String pomFilePath = directory + File.separator + "pom.xml";
        Path pomPath = Paths.get(pomFilePath);
        if (!Files.isRegularFile(pomPath)) {
            throw new RuntimeException(String.format("Directory: %s does not contain pom.xml", directory));
        }

        allowProcessing();

        // Open the pom file and remove any exclusions and forced transitive dependencies
        try {
            PomManipulator pomManipulator = new PomManipulator((pomFilePath));
            pomManipulator.stripExclusions();
            pomManipulator.stripForcedTransitiveDependencies();
            pomManipulator.saveFile();
        } catch (Exception e) {
            throw new RuntimeException("Problem processing pom file: " + pomFilePath, e);
        }

        // Run maven build - see if there are any dependency conflicts
        // TODO: This may be possible programmatically from just the dependency:tree

        // run maven dependency:tree
        Path tempFilePath = null;
        TgfData tgfData = null;
        try {
            tempFilePath = Files.createTempFile("dependency-resolver-", ".tgf.tmp");
            executeCommand(directoryFile, MVN_COMMAND, "dependency:tree",
                    "-DoutputType=tgf", "-DoutputFile=" + tempFilePath.toString());
            tgfData = new TgfData(tempFilePath.toString());
        } catch (Exception e) {
            throw new RuntimeException("Problem with dependency resolution", e);
        } finally {
            if (tempFilePath != null) {
                new File(tempFilePath.toString()).delete();
            }
        }

        // parse out TGF Data

        // Aggregate results / figure out what to do

        // Update pom.xml
        try {
            PomManipulator pomManipulator = new PomManipulator((pomFilePath));

            // Update dependencies

            pomManipulator.saveFile();
        } catch (Exception e) {
            throw new RuntimeException("Problem processing pom file: " + pomFilePath, e);
        }

        // Happiness
    }

    /**
     * Ensure that we can process this request / warn the user
     */
    protected void allowProcessing() {
        if (!skipPrompts) {
            while (true) {
                try {
                    System.out.print("This is destructive - are you sure you want to continue (y/n)?: ");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String value = reader.readLine().toLowerCase();

                    if (value.matches("y(es)?")) {
                        break;
                    }

                    if (value.matches("no?")) {
                        System.exit(0);
                    }
                } catch (IOException e) { }
            }
        }
    }

    private void executeCommand(File directoryFile, String... commandAndParams) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandAndParams);
            //builder.inheritIO(); // TODO: Learn what this does - weird things with consuming output
            builder.environment().putAll(environmentVars);
            builder.directory(directoryFile);
            Process process = builder.start();

            int exitValue = process.waitFor();
            BufferedReader reader = (exitValue == 0)
                    ? new BufferedReader(new InputStreamReader(process.getInputStream()))
                    : new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line = "";
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            if (exitValue != 0) {
                throw new RuntimeException(String.format("Process: %s failed with status code %d",
                        Arrays.stream(commandAndParams).toArray().toString(), exitValue));
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("Checksum").build()
                .defaultHelp(true)
                .description("Resolve conflicting dependencies (exclusions).");
        parser.addArgument("-e", "--env")
                .type(String.class)
                .required(false)
                .help("Specify environment variables (comma separated, k=v pairs");
        parser.addArgument("-s", "--skipPrompts")
                .type(Boolean.class)
                .setDefault(false)
                .help("Specify to skip any prompts");
        parser.addArgument("directory")
                .type(String.class)
                .required(true)
                .help("directory of project to modify");
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        String env = ns.getString("env");
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

        boolean skipPrompts = ns.getBoolean("skipPrompts");
        String directory = ns.getString("directory");

        DependencyResolver resolver = new DependencyResolver(directory, environmentVars, skipPrompts);
        resolver.process();
    }
}
