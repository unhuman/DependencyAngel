package com.unhuman.dependencyangel;

import com.unhuman.dependencyangel.convergence.*;
import com.unhuman.dependencyangel.pom.PomManipulator;
import com.unhuman.dependencyangel.versioning.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.unhuman.dependencyangel.convergence.ConvergenceParser.CONVERGE_ERROR;
import static java.lang.System.exit;

public class DependencyAngel {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private static final String TEMP_FILE_PREFIX = "dependency-angel-";
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

    private String getPomFilePath() {
        return config.getDirectory() + File.separator + "pom.xml";
    }
    protected void process() {
        File directoryFile = new File(config.getDirectory()).getAbsoluteFile();
        if (!directoryFile.isDirectory()) {
            throw new RuntimeException(String.format("%s is not a directory", config.getDirectory()));
        }

        Path pomPath = Paths.get(getPomFilePath());
        if (!Files.isRegularFile(pomPath)) {
            throw new RuntimeException(String.format("Directory: %s does not contain pom.xml", config.getDirectory()));
        }

        allowProcessing();

        // Open the pom file and remove any exclusions and forced transitive dependencies
        performPomCleanup();

        if (config.isCleanOnly()) {
            return;
        }

        // this processing may take multiple iterations if there are nested dependencies
        List<DependencyConflict> conflicts;
        int iteration = 0;
        do {
            List<String> analyzeResults;
            try {
                analyzeResults = executeCommand(directoryFile, CONVERGE_ERROR, MVN_COMMAND,
                        "dependency:analyze");
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Problem with analyze", e);
            }

            ConvergenceParser convergenceParser = ConvergenceParser.from(analyzeResults);
            conflicts = new ArrayList<>(convergenceParser.getDependencyConflicts());
            System.out.println(String.format("Iteration %d: %d conflicts remaining",
                    ++iteration, conflicts.size()));

            List<ResolvedDependencyDetailsList> workList = calculatePomChanges(conflicts);
            updatePomFile(workList);
        } while (conflicts.size() > 0);

        // Happiness
    }

    private void performPomCleanup() {
        if (!config.isNoClean()) {
            try {
                PomManipulator pomManipulator = new PomManipulator(getPomFilePath());
                pomManipulator.stripExclusions();
                pomManipulator.stripForcedTransitiveDependencies();
                pomManipulator.saveFile();
                System.out.println("pom.xml file cleaned");
            } catch (Exception e) {
                throw new RuntimeException("Problem processing pom file: " + getPomFilePath(), e);
            }
        }
    }

    /**
     * Calculate pom changes
     *
     * @param conflicts - usage is destructive and will be altered
     * @return
     */
    private List<ResolvedDependencyDetailsList> calculatePomChanges(List<DependencyConflict> conflicts) {
        // shallow copy the conflict locally so we can mutate the list
        DependencyProcessState dependencyProcessState = new DependencyProcessState(conflicts);

        List<ResolvedDependencyDetailsList> workList = new ArrayList<>();
        while (dependencyProcessState.hasNext()) {
            DependencyConflict currentConflict = dependencyProcessState.next();

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
            if (dependencyProcessState.hasNext()) {
                DependencyConflict nextConflict = dependencyProcessState.peekNext();

                if (dependencyProcessState.hasHandledDependency(nextConflict)) {
                    // clear any handled dependencies for the next iteration
                    dependencyProcessState.resetHandledDependencies();
                    break;
                }
            }

            // nothing handled indicates we need another cycle
            if (!dependencyProcessState.hasHandledDependencies()) {
                break;
            }
        }
        return workList;
    }

    private void updatePomFile(List<ResolvedDependencyDetailsList> workList) {
        // Update pom.xml
        try {
            PomManipulator pomManipulator = new PomManipulator(getPomFilePath());

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
            throw new RuntimeException("Problem processing pom file: " + getPomFilePath(), e);
        }
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
                        exit(0);
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
        System.out.println("Executing: " + Arrays.stream(commandAndParams).collect(Collectors.joining(" ")));
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
        try {
            DependencyAngelConfig config = new DependencyAngelConfig(args);
            DependencyAngel angel = new DependencyAngel(config);
            angel.process();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            exit(-1);
        }
    }

    /**
     * Internal state for how we process dependencies
     * This is destructive
     */
    class DependencyProcessState implements Iterator<DependencyConflict> {
        List<DependencyConflict> conflicts;
        List<DependencyConflict> handledDependencies;

        /**
         * Manipulation of conflicts is destructive
         * @param conflicts
         */
        DependencyProcessState(List conflicts) {
            this.conflicts = conflicts;
            handledDependencies = new ArrayList<>();
        }

        @Override
        public boolean hasNext() {
            return (conflicts.size() > 0);
        }

        @Override
        public DependencyConflict next() {
            DependencyConflict conflict = conflicts.remove(0);
            handledDependencies.add(conflict);
            return conflict;
        }

        public DependencyConflict peekNext() {
            return conflicts.get(0);
        }


            public boolean hasHandledDependency(DependencyConflict check) {
            for (DependencyConflict handledDependency: handledDependencies) {
                if (check.containsDependency(handledDependency)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasHandledDependencies() {
            return (handledDependencies.size() > 0);
        }

        public void resetHandledDependencies() {
            handledDependencies.clear();
        }
    }
}
