package com.unhuman.dependencyresolver;

import com.unhuman.dependencyresolver.convergence.ConvergenceParser;
import com.unhuman.dependencyresolver.convergence.DependencyConflict;
import com.unhuman.dependencyresolver.convergence.DependencyConflictData;
import com.unhuman.dependencyresolver.pom.PomManipulator;
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
import java.util.*;
import java.util.regex.Pattern;

import static com.unhuman.dependencyresolver.convergence.ConvergenceParser.CONVERGE_ERROR;

public class DependencyResolver {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private static final String TEMP_FILE_PREFIX = "dependency-resolver-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final Pattern GENERATED_EXPECTED_FILE_LINE =
            Pattern.compile(String.format("Wrote dependency tree to:.*%s.*%s",
                    TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX));
    private static final Pattern CONVERGENCE_EXPECTED_FILE_LINE =
            Pattern.compile("DependencyConvergence failed with message");

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

//        // run maven dependency:tree
//        Path tempTgfFilePath = null;
//        TgfData tgfData;
//        try {
//            tempTgfFilePath = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
//
//            executeCommand(directoryFile, GENERATED_EXPECTED_FILE_LINE, MVN_COMMAND, "dependency:tree",
//                    "-DoutputType=tgf", "-DoutputFile=" + tempTgfFilePath.toString());
//            tgfData = TgfProcessor.process(tempTgfFilePath.toString());
//        } catch (RuntimeException re) {
//            throw re;
//        } catch (Exception e) {
//            throw new RuntimeException("Problem with dependency resolution", e);
//        } finally {
//            if (tempTgfFilePath != null) {
//                new File(tempTgfFilePath.toString()).delete();
//            }
//        }
//
//        // parse out TGF Data
//        DependencyNode root = DependencyHelper.convertTgfData(tgfData);

        // this processing may take multiple iterations if there are nested dependencies
        List<DependencyConflict> conflicts;
        do {
            try {
                List<String> analyzeResults = executeCommand(directoryFile, CONVERGE_ERROR, MVN_COMMAND,
                        "dependency:analyze");

                ConvergenceParser convergenceParser = ConvergenceParser.from(analyzeResults);
                conflicts = convergenceParser.getDependencyConflicts();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Problem with analyze", e);
            }

            conflicts = new ArrayList<>(conflicts);
            Iterator<DependencyConflict> conflictIterator = conflicts.iterator();
            while(conflictIterator.hasNext()) {
                DependencyConflict currentConflict = conflictIterator.next();
                System.out.println("Processing conflict: " + currentConflict.getDisplayName()
                        + " to version: " + currentConflict.getVersion()
                        + " with scope: " + currentConflict.getScope());

                conflictIterator.remove();
            }

            // Update pom.xml
            try {
                PomManipulator pomManipulator = new PomManipulator((pomFilePath));

                // Update dependencies

                pomManipulator.saveFile();
            } catch (Exception e) {
                throw new RuntimeException("Problem processing pom file: " + pomFilePath, e);
            }
        } while (conflicts.size() > 0);

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
     * @return list of data in the input
     */
    protected List<String> executeCommand(File directoryFile, Pattern errorMatchForSuccess, String... commandAndParams) {
        List<String> output = new ArrayList<>(100);
        try {
            ProcessBuilder builder = new ProcessBuilder(commandAndParams);
            //builder.inheritIO(); // TODO: Learn what this does - weird things with consuming output
            builder.environment().putAll(environmentVars);
            builder.directory(directoryFile);
            Process process = builder.start();

            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line = "";
            boolean foundDesiredValue = false;

            // If there was an error exit value, search to see if we should treat it as success
            if (errorMatchForSuccess != null) {
                while ((line = outputReader.readLine()) != null) {
                    if (!foundDesiredValue && errorMatchForSuccess.matcher(line).find()) {
                        System.out.println("Found desired line: " + errorMatchForSuccess);
                        foundDesiredValue = true;
                    }
                    output.add(line);
                }
                if (!foundDesiredValue) {
                    throw new RuntimeException(String.format("Could not find desired value in output: %s",
                            errorMatchForSuccess));
                }
            }

            // output any errors we got
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }

            int processResult =  process.waitFor();

            if (processResult !=0 && !foundDesiredValue) {
                throw new RuntimeException(String.format("Process: %s failed with status code %d",
                        Arrays.stream(commandAndParams).toArray().toString(), processResult));
            }

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
        return output;
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
