package com.unhuman.dependencyresolver;

import com.unhuman.dependencyresolver.pom.PomManipulator;
import com.unhuman.dependencyresolver.tgf.TgfProcessor;
import com.unhuman.dependencyresolver.tree.DependencyNode;
import com.unhuman.dependencyresolver.utility.DependencyHelper;
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
import java.util.regex.Pattern;

public class DependencyResolver {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private static final String TEMP_TGF_FILE_PREFIX = "dependency-resolver-";
    private static final String TEMP_TGF_FILE_SUFFIX = ".tgf.tmp";
    private static final Pattern GENERATED_EXPECTED_FILE_LINE =
            Pattern.compile(String.format("Wrote dependency tree to:.*%s.*%s",
                    TEMP_TGF_FILE_PREFIX, TEMP_TGF_FILE_SUFFIX));

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
        TgfProcessor tgfProcessor = null;
        try {
            tempFilePath = Files.createTempFile(TEMP_TGF_FILE_PREFIX, TEMP_TGF_FILE_SUFFIX);

            executeCommand(directoryFile, GENERATED_EXPECTED_FILE_LINE, MVN_COMMAND, "dependency:tree",
                    "-DoutputType=tgf", "-DoutputFile=" + tempFilePath.toString());
            tgfProcessor = new TgfProcessor(tempFilePath.toString());
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Problem with dependency resolution", e);
        } finally {
            if (tempFilePath != null) {
                new File(tempFilePath.toString()).delete();
            }
        }

        // parse out TGF Data
        DependencyNode root = DependencyHelper.convertTgfData(tgfProcessor.getTgfData());

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

    /**
     *
     * @param directoryFile
     * @param errorMatchForSuccess - search output for a pattern under error conditions - if found, treat as success
     * @param commandAndParams
     */
    protected void executeCommand(File directoryFile, Pattern errorMatchForSuccess, String... commandAndParams) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commandAndParams);
            //builder.inheritIO(); // TODO: Learn what this does - weird things with consuming output
            builder.environment().putAll(environmentVars);
            builder.directory(directoryFile);
            Process process = builder.start();

            int exitValue = process.waitFor();
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line = "";

            // If there was an error exit value, search to see if we should treat it as success
            if (errorMatchForSuccess != null) {
                // Assume we aren't going to find what we're looking for
                boolean foundDesiredValue = false;
                while ((line = outputReader.readLine()) != null) {
                    if (errorMatchForSuccess.matcher(line).find()) {
                        System.out.println("Found desired output: " + errorMatchForSuccess);
                        foundDesiredValue = true;
                        exitValue = 0;
                        break;
                    }
                }
                if (!foundDesiredValue) {
                    throw new RuntimeException(String.format("Could not find desired value in output: %s",
                            errorMatchForSuccess));
                }
            }

            if (exitValue != 0) {
                // output any errors we got
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }

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
