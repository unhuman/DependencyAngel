package com.unhuman.dependencyangel;

import com.unhuman.dependencyangel.convergence.*;
import com.unhuman.dependencyangel.pom.PomManipulator;
import com.unhuman.dependencyangel.versioning.Version;
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

import static com.unhuman.dependencyangel.convergence.ConvergenceParser.CONVERGE_ERROR;

public class DependencyAngel {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private static final String TEMP_FILE_PREFIX = "dependency-resolver-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final Pattern GENERATED_EXPECTED_FILE_LINE =
            Pattern.compile(String.format("Wrote dependency tree to:.*%s.*%s",
                    TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX));
    private static final Pattern CONVERGENCE_EXPECTED_FILE_LINE =
            Pattern.compile("DependencyConvergence failed with message");

    DependencyAngelConfig config;

    protected DependencyAngel(DependencyAngelConfig config) {
        this.config = config;
    }

    protected void process() {
        File directoryFile = new File(config.getDirectory()).getAbsoluteFile();
        if (!directoryFile.isDirectory()) {
            throw new RuntimeException(String.format("Directory: %s is not a directory", config.getDirectory()));
        }

        String pomFilePath = config.getDirectory() + File.separator + "pom.xml";
        Path pomPath = Paths.get(pomFilePath);
        if (!Files.isRegularFile(pomPath)) {
            throw new RuntimeException(String.format("Directory: %s does not contain pom.xml", config.getDirectory()));
        }

        allowProcessing();

        // Open the pom file and remove any exclusions and forced transitive dependencies
        try {
            PomManipulator pomManipulator = new PomManipulator((pomFilePath));
            pomManipulator.stripExclusions();
            pomManipulator.stripForcedTransitiveDependencies();
            pomManipulator.saveFile();
            System.out.println("pom.xml file cleaned");
        } catch (Exception e) {
            throw new RuntimeException("Problem processing pom file: " + pomFilePath, e);
        }
        if (config.isCleanOnly()) {
            return;
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
        int itemsToProcess = -1;
        List<DependencyConflict> conflicts;
        int iteration = 0;
        do {
            try {
                List<String> analyzeResults = executeCommand(directoryFile, CONVERGE_ERROR, MVN_COMMAND,
                        "dependency:analyze");

                ConvergenceParser convergenceParser = ConvergenceParser.from(analyzeResults);
                conflicts = convergenceParser.getDependencyConflicts();
                System.out.println(String.format("Iteration %d: %d conflicts remaining",
                        ++iteration, conflicts.size()));

                // Track if we are stuck - shouldn't happen, but let's be safe
                if (conflicts.size() > 0 && itemsToProcess == conflicts.size()) {
                    throw new RuntimeException("Problems determining changes - stuck in loop");
                }
                itemsToProcess = conflicts.size();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Problem with analyze", e);
            }

            conflicts = new ArrayList<>(conflicts);
            List<DependencyConflict> handledDependencies = new ArrayList<>();

            List<ResolvedDependencyDetailsList> workList = new ArrayList<>();
            while (conflicts.size() > 0) {
                DependencyConflict currentConflict = conflicts.remove(0);
                handledDependencies.add(currentConflict);

                System.out.println("Processing conflict: " + currentConflict.getDisplayName()
                        + " to version: " + currentConflict.getVersion()
                        + " with scope: " + currentConflict.getScope());

                // Determine actions to be performed
                ResolvedDependencyDetailsList workToDo = new ResolvedDependencyDetailsList();
                for (DependencyConflictData data: currentConflict.getConflictHierarchy()) {
                    workToDo.add(data.getEndDependencyInfo());
                }
                workList.add(workToDo);

                // don't process further if the next dependency includes anything changed by this one
                // that'd be handled in the next iteration
                if (conflicts.size() > 0) {
                    DependencyConflict nextConflict = conflicts.get(0);
                    for (DependencyConflict handledDependency: handledDependencies) {
                        if (nextConflict.containsDependency(handledDependency)) {
                            // clear the handled dependencies for the next iteration
                            handledDependencies.clear();
                            break;
                        }
                    }
                }

                // nothing handled indicates we need another cycle
                if (handledDependencies.size() == 0) {
                    break;
                }
            }

            // Update pom.xml
            try {
                PomManipulator pomManipulator = new PomManipulator((pomFilePath));

                // Update dependencies
                for (ResolvedDependencyDetailsList workItem: workList) {
                    // Determine the required scope and version
                    String explicitDependencyScope = (workItem.getResolvedScope() != null)
                            ? workItem.getResolvedScope() : "compile";
                    Version explicitVersion = (workItem.getLatestVersion());

                    boolean needsExplicitDependency = true;
                    for (ResolvedDependencyDetails workDependency: workItem) {
                        if (workDependency.isExplicitDependency()) {
                            needsExplicitDependency = false;
                            // update the explicit dependency with version + scope
                            pomManipulator.updateExplicitVersion(
                                    workDependency.getInitialDependency().getGroup(),
                                    workDependency.getInitialDependency().getArtifact(),
                                    workItem.getLatestVersion(), explicitDependencyScope);
                        }
                        if (workDependency.needsExclusion(explicitVersion)) {
                            // exclude the dependency
                            pomManipulator.addExclusion(workDependency.getInitialDependency().getGroup(),
                                    workDependency.getInitialDependency().getArtifact(),
                                    workItem.getGroup(), workItem.getArtifact());
                        }
                        // else is scope satisfied here - if it was, we don't need explicit dependency
                    }

                    if (needsExplicitDependency) {
                        pomManipulator.addForcedDependencyNode(workItem.getGroup(), workItem.getArtifact(),
                                workItem.getLatestVersion(), explicitDependencyScope);
                    }
                }

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
        if (!config.isSkipPrompts()) {
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
            builder.environment().putAll(config.getEnvironmentVars());
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
        parser.addArgument("-c", "--cleanOnly")
                .type(Boolean.class)
                .required(false)
                .setDefault(false)
                .help("Perform clean up only");
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

        DependencyAngelConfig config = new DependencyAngelConfig(ns.getString("directory"));
        config.addEnvironmentVars(getEnvParameterMap(ns.getString("env")));
        config.setCleanOnly(ns.getBoolean("cleanOnly"));
        config.setSkipPrompts(ns.getBoolean("skipPrompts"));

        DependencyAngel resolver = new DependencyAngel(config);
        resolver.process();
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
