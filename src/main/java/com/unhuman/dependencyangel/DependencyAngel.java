package com.unhuman.dependencyangel;

import com.unhuman.dependencyangel.convergence.ConvergenceParser;
import com.unhuman.dependencyangel.convergence.DependencyConflict;
import com.unhuman.dependencyangel.convergence.DependencyConflictData;
import com.unhuman.dependencyangel.convergence.ResolvedDependencyDetails;
import com.unhuman.dependencyangel.convergence.ResolvedDependencyDetailsList;
import com.unhuman.dependencyangel.dependency.Dependency;
import com.unhuman.dependencyangel.pom.PomManipulator;
import com.unhuman.dependencyangel.versioning.Version;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.unhuman.dependencyangel.convergence.ConvergenceParser.CONVERGE_ERROR;
import static com.unhuman.dependencyangel.pom.PomManipulator.ARTIFACT_ID_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.CLASSIFIER_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.DEPENDENCY_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.EXCLUSIONS_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.EXCLUSION_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.GROUP_ID_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.PROPERTIES_VARIABLE;
import static com.unhuman.dependencyangel.pom.PomManipulator.SCOPE_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.TYPE_TAG;
import static com.unhuman.dependencyangel.pom.PomManipulator.VERSION_TAG;
import static java.lang.System.exit;

public class DependencyAngel {
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final String MVN_COMMAND = (IS_WINDOWS) ? "mvn.cmd" : "mvn";
    private static final String TEMP_FILE_PREFIX = "dependency-angel-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    public static final Pattern BANNED_ERROR = Pattern.compile(
            "Found Banned Dependency: (.*?)");
    private static final Pattern GENERATED_EXPECTED_FILE_LINE =
            Pattern.compile(String.format("Wrote dependency tree to:.*%s.*%s",
                    TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX));
    private static final Pattern CONVERGENCE_EXPECTED_FILE_LINE =
            Pattern.compile("DependencyConvergence failed with message");

    DependencyAngelConfig config;
    List<File> nestedPoms;

    // Flag to track this so we don't prompt multiple times
    private boolean allowProcessing = false;

    protected DependencyAngel(DependencyAngelConfig config) {
        this.config = config;

        // Determine nested poms
        nestedPoms = findChildPomFiles(config.getDirectory(), config.getDirectory());
    }

    private static String getPomFilePath(String directoryOrFile) {
        String suffix = File.separator + "pom.xml";
        return (directoryOrFile.endsWith(suffix)) ? directoryOrFile : directoryOrFile + suffix;
    }

    protected File prepareOperation(String directory) {
        File directoryFile = new File(directory).getAbsoluteFile();
        if (!directoryFile.isDirectory()) {
            throw new RuntimeException(String.format("%s is not a directory", directory));
        }

        Path pomPath = Paths.get(getPomFilePath(directory));
        if (!Files.isRegularFile(pomPath)) {
            throw new RuntimeException(String.format("Directory: %s does not contain pom.xml", directory));
        }

        allowProcessing();

        return directoryFile;
    }

    protected void setupDependencyManagement() throws ParserConfigurationException, IOException, SAXException {
        if (!config.performSetup()) {
            return;
        }

        // Ensure we are in a valid place to start
        prepareOperation(config.getDirectory());

        // Open the pom file and remove any exclusions and forced transitive dependencies
        performPomCleanup(config.getDirectory());

        // Create a manipulator for the parent pom, so we can validate it correctly
        PomManipulator parentPomManipulator = new PomManipulator(getPomFilePath(config.getDirectory()));

        parentPomManipulator.ensureDependencyManagement();

        // build up a list of subdirectories with pom.xml in them
        for (File nestedPom: nestedPoms) {
            performPomCleanup(nestedPom.getAbsolutePath());
        }

        // preserved exclusions and banned dependencies are both treated the same (skip existing exclusions)
        // TODO: this is duplicated in PomManipulator
        Set<String> preserveExclusions = new HashSet<>(
                config.getBannedDependencies().size() + config.getPreserveExclusions().size());
        preserveExclusions.addAll(config.getBannedDependencies());
        preserveExclusions.addAll(config.getPreserveExclusions());

        // in main pom.xml, validate / check dependency management
        List<Dependency> dependenciesToManage = new ArrayList<>();
        for (File nestedPom: nestedPoms) {
            PomManipulator nestedManipulator = new PomManipulator(nestedPom.getAbsolutePath());

            Node dependenciesNode = nestedManipulator.getDependenciesNode();
            if (dependenciesNode == null) {
                // nothing to do here
                continue;
            }
            List<Node> dependencyNodes = nestedManipulator.findChildElements(dependenciesNode, DEPENDENCY_TAG);
            for (Node dependencyNode : dependencyNodes) {
                String groupId = nestedManipulator.getSingleNodeElement(dependencyNode, GROUP_ID_TAG, true)
                        .getTextContent();
                String artifactId = nestedManipulator.getSingleNodeElement(dependencyNode, ARTIFACT_ID_TAG, true)
                        .getTextContent();

                Version version = null;
                Node versionNode = nestedManipulator.getSingleNodeElement(dependencyNode, VERSION_TAG, false);
                Node versionPropertyNode = null;
                if (versionNode != null) {
                    String versionText = versionNode.getTextContent();
                    // See if version is defined in properties
                    Matcher propertiesVersionMatcher = PROPERTIES_VARIABLE.matcher(versionText);
                    if (propertiesVersionMatcher.matches()) {
                        versionPropertyNode = nestedManipulator.findSingleElement(versionText, false);

                        // We have a version variable - ensure we can find it - otherwise, we do not process this
                        if (versionPropertyNode == null) {
                            continue;
                        }

                        versionText = versionPropertyNode.getTextContent();

                        // If we find a version property, but can't lookup the value, then we can assume
                        // this is a project-level dependency.
                    }
                    version = (versionText != null) ? new Version(versionText) : null;
                }

                // No version - we don't process this
                if (version == null) {
                    continue;
                }

                Node classifierNode = nestedManipulator.getSingleNodeElement(dependencyNode, CLASSIFIER_TAG, false);
                String classifier = (classifierNode != null) ? classifierNode.getTextContent() : null;

                Node scopeNode = nestedManipulator.getSingleNodeElement(dependencyNode, SCOPE_TAG, false);
                String scope = (scopeNode != null) ? scopeNode.getTextContent() : null;

                Node typeNode = nestedManipulator.getSingleNodeElement(dependencyNode, TYPE_TAG, false);
                String type = (typeNode != null) ? typeNode.getTextContent() : null;

                // only strip exclusions whose parent node is a dependency
                List<Dependency> exclusions = new ArrayList<>();
                Node exclusionsNode =
                        nestedManipulator.getSingleNodeElement(dependencyNode, EXCLUSIONS_TAG, false);
                if (exclusionsNode != null) {
                    List<Node> exclusionNodes =
                            nestedManipulator.findChildElements(exclusionsNode, EXCLUSION_TAG);
                    for (Node exclusionNode : exclusionNodes) {
                        String exclusionGroupId = nestedManipulator.getSingleNodeElement
                                (exclusionNode, GROUP_ID_TAG, true).getTextContent();
                        String exclusionArtifactId = nestedManipulator.getSingleNodeElement
                                (exclusionNode, ARTIFACT_ID_TAG, true).getTextContent();
                        // This check will not miss if clean has occurred.
                        if (preserveExclusions.contains(
                                String.format("%s:%s", exclusionGroupId, exclusionArtifactId))) {
                            exclusions.add(new Dependency(exclusionGroupId, exclusionArtifactId));
                        }
                        // We delete all the exclusions below, so we don't need to do this.
                        // nestedManipulator.deleteNode(exclusionNode, true);
                    }
                }
                nestedManipulator.deleteNode(exclusionsNode, true);

                // Create a dependency to update info and remove related child nodes from the document
                Dependency dependency = new Dependency(groupId, artifactId, type, version, scope, classifier);
                dependency.setExclusions(exclusions);

                dependenciesToManage.add(dependency);

                nestedManipulator.deleteNode(typeNode, true);
                nestedManipulator.deleteNode(versionNode, true);
                nestedManipulator.deleteNode(versionPropertyNode, true);

                // We don't delete scope or classifier
                // nestedManipulator.deleteNode(scopeNode, true);
                // nestedManipulator.deleteNode(classifierNode, true);
            }

            nestedManipulator.saveFile(null, "Nested dependency management handled");
        }

        // Now update the parent pom to have all the dependencies
        for (Dependency dependency: dependenciesToManage) {
            // Add or Update (handling version) the dependency
            if (!parentPomManipulator.updateExplicitVersion(dependency)) {
                parentPomManipulator.addDependencyNode(dependency);
            }
        }
        parentPomManipulator.saveFile(null, "Parent dependency management handled");

        // Happiness
    }

    protected void process() {
        if (!config.performProcess()) {
            return;
        }

        // Read in all the pom files to build up the static state of knowing internal artifacts
        // TODO: Improve this!
        new PomManipulator(getPomFilePath(config.getDirectory()));
        // build up a list of subdirectories with pom.xml in them
        for (File nestedPom: nestedPoms) {
            new PomManipulator(nestedPom.getAbsolutePath());
        }

        File directoryFile = prepareOperation(config.getDirectory());

        // this processing may take multiple iterations if there are nested dependencies
        List<DependencyConflict> conflicts;
        int iteration = 0;
        while (true) {
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

            // We are done when there are no conflicts detected
            if (conflicts.size() == 0) {
                break;
            }

            List<ResolvedDependencyDetailsList> workList = calculatePomChanges(conflicts);
            updatePomFile(workList);

            if (config.performProcessSingleStep()) {
                return;
            }
        }

        // Happiness
    }

    protected void exclusionReduction() {
        if (!config.performExclusionReduction()) {
            return;
        }

        // Update pom.xml
        PomManipulator pomManipulator = new PomManipulator(getPomFilePath(config.getDirectory()));

        if (!pomManipulator.hasDependencyManagement()) {
            return;
        }

        // Find all the items in dependencyManagement
        List<Node> dependencyNodes =
                pomManipulator.findChildElements(pomManipulator.getDependenciesNode(), DEPENDENCY_TAG);

        // Create a list of all dependencies
        List<Dependency> managedDependencies = new ArrayList<>(dependencyNodes.size());
        for (Node dependencyNode: dependencyNodes) {
            Dependency dependency = new Dependency(
                    pomManipulator.getSingleNodeElement(dependencyNode, GROUP_ID_TAG, true).getTextContent(),
                    pomManipulator.getSingleNodeElement(dependencyNode, ARTIFACT_ID_TAG, true).getTextContent());
            managedDependencies.add(dependency);
        }

        // Check all other items for exclusion for that item (and remove it)
        for (Node dependencyNode: dependencyNodes) {
            Node exclusionsNode = pomManipulator.getSingleNodeElement(dependencyNode, EXCLUSIONS_TAG, false);
            if (exclusionsNode == null) {
                continue;
            }

            List<Node> exclusionNodes =
                    pomManipulator.findChildElements(exclusionsNode, EXCLUSION_TAG);

            int counter = exclusionNodes.size();
            for (Node exclusionNode: exclusionNodes) {
                String exclusionGroupId =
                        pomManipulator.getSingleNodeElement(exclusionNode, GROUP_ID_TAG, true).getTextContent();
                String exclusionArtifactId =
                        pomManipulator.getSingleNodeElement(exclusionNode, ARTIFACT_ID_TAG, true).getTextContent();

                for (Dependency managedDependency: managedDependencies) {
                    if (managedDependency.getGroup().equals(exclusionGroupId)
                            && managedDependency.getArtifact().equals(exclusionArtifactId)) {
                        // either delete the exclusion or exclusions node if nothing left
                        if (--counter == 0) {
                            pomManipulator.deleteNode(exclusionsNode, true);
                        } else {
                            pomManipulator.deleteNode(exclusionNode, true);
                        }
                    }
                }
            }
        }

        pomManipulator.saveFile("No exclusion reduction required", "Exclusion reduction performed");

        // Happiness
    }

    /**
     *
     * @param directoryName
     * @param ignoreDirectoryName (don't find pom files for this directory (root)
     * @return
     */
    private static List<File> findChildPomFiles(String directoryName, String ignoreDirectoryName) {
        File directory = new File(directoryName);

        List<File> childPoms = new ArrayList<>();

        // get all the files from a directory
        File[] checkFiles = directory.listFiles();
        for (File checkFile: checkFiles) {
            if (!directoryName.equals(ignoreDirectoryName) && checkFile.isFile() && checkFile.getName().equals("pom.xml")) {
                childPoms.add(checkFile);
            } else if (checkFile.isDirectory()) {
                childPoms.addAll(findChildPomFiles(checkFile.getAbsolutePath(), ignoreDirectoryName));
            }
        }
        return childPoms;
    }

    private void performPomCleanup(String directoryOrPomFilePath) {
        String pomFilePath = getPomFilePath(directoryOrPomFilePath);
        PomManipulator pomManipulator = new PomManipulator(pomFilePath);
        pomManipulator.stripExclusions(config);
        pomManipulator.stripDependencyAngelDependencies();
        pomManipulator.saveFile(null, "pom cleaned");
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
            // Keep track if there's any weird things going on here - self references...
            // They will need to be handled explicitly
            for (DependencyConflictData data: currentConflict.getConflictHierarchy()) {
                if (data.getEndDependencyInfo() == null) {
                    List<String> checkInclusions = new ArrayList<>();
                    for (DependencyConflictData data2: currentConflict.getConflictHierarchy()) {
                        if (data2.getEndDependencyInfo() != null) {
                            checkInclusions.add(String.format("%s:%s",
                                    data2.getEndDependencyInfo().getInitialDependency().getGroup(),
                                    data2.getEndDependencyInfo().getInitialDependency().getArtifact()));
                        }
                    }
                    throw new RuntimeException(String.format("Error handling %s:%s - check for dependency loop " +
                                    "from these inclusions [%s]",
                            data.getGroup(), data.getArtifact(),
                            String.join(",", checkInclusions)));
                }
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
        PomManipulator pomManipulator = new PomManipulator(getPomFilePath(config.getDirectory()));

        // Update dependencies
        for (ResolvedDependencyDetailsList workItem: workList) {
            // TODO: This is busted - gds-hub
            // If there is a self referential (circular dependency) item in here, we need to manage exclusions
            boolean circularDependency = false;
            for (ResolvedDependencyDetails details: workItem) {
                // if a top level dependency is this guy and is the latest version - it's our own, so - no version
                // TODO: maybe we can check top level artifact, too
                if (PomManipulator.isKnownArtifact(workItem.getGroup(), workItem.getArtifact())) {
                    circularDependency = true;
                }
//                if (details.getInitialDependency().getGroup().equals(workItem.getGroup())
//                        && details.getInitialDependency().getArtifact().equals(workItem.getArtifact())
//                        && !details.getInitialDependency().getVersion().equals(workItem.getLatestVersion())) {
//                    circularDependency = true;
//                    break;
//                }
            }

            boolean needsExplicitDependency = true;
            if (circularDependency) {
                System.out.println("Circular dependency found: " + workItem.getArtifact());
                // Determine the required scope and version
                Version explicitVersion = workItem.getLatestVersion();

                for (ResolvedDependencyDetails workDependency: workItem) {
                    if (workDependency.isExplicitDependency()) {
                        needsExplicitDependency = false;
                        System.out.println("Updating version: " + workItem.getArtifact());
                        // update the explicit dependency with version + scope
                        pomManipulator.updateExplicitVersion(
                                workDependency.getInitialDependency().getGroup(),
                                workDependency.getInitialDependency().getArtifact(),
                                workItem.getResolvedType(), workItem.getLatestVersion(),
                                workItem.getResolvedScope(), workItem.getResolvedClassifier(), null);
                    }
                    if (workDependency.needsExclusion(explicitVersion)) {
                        // exclude the dependency
                        System.out.println("Excluding: " + workItem.getArtifact() + " from: " +
                                        workDependency.getInitialDependency().getArtifact());
                        pomManipulator.addExclusion(workDependency.getInitialDependency().getGroup(),
                                workDependency.getInitialDependency().getArtifact(),
                                workItem.getGroup(), workItem.getArtifact());
                    }
                    // else is scope satisfied here - if it was, we don't need explicit dependency
                }
            }

            if (needsExplicitDependency) {
                pomManipulator.addForcedDependencyNode(workItem.getGroup(), workItem.getArtifact(),
                        workItem.getResolvedType(), workItem.getLatestVersion(), workItem.getResolvedScope(),
                        workItem.getResolvedClassifier(), null);

                // Figure out if we had a conflicted item that brought in multiple versions of this dependency
                // if we did, we need to explicitly add a dependency to any user of that library
                for (File nestedPom : nestedPoms) {
                    PomManipulator nestedManipulator = new PomManipulator(nestedPom.getAbsolutePath());

                    // Any place we find the dependency, we need to strip out the version
                    // Scan the child poms (maybe we can track those)
                    Node dependencyNode = nestedManipulator.findDependency(workItem.getGroup(), workItem.getArtifact());
                    if (dependencyNode != null) {
                        List<Node> versionNodes = nestedManipulator.findChildElements(dependencyNode, VERSION_TAG);
                        for (Node versionNode : versionNodes) {
                            nestedManipulator.deleteNode(versionNode, true);
                        }
                    }

                    nestedManipulator.saveFile(null, "updated nested pom file");
                }
            }
        }

        pomManipulator.saveFile(null, "updated pom file");
    }

    /**
     * Ensure that we can process this request / warn the user
     */
    protected void allowProcessing() {
        if (allowProcessing || config.isSkipPrompts()) {
            // we allow processing if skipping prompts
            allowProcessing = true;
        } else {
            while (true) {
                try {
                    System.out.print("This is destructive - are you sure you want to continue (y/N)?: ");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                    String value = reader.readLine().toLowerCase();

                    if (value.matches("y(es)?")) {
                        // track we accepted this, so we don't prompt multiple times
                        allowProcessing = true;
                        break;
                    }

                    if (value.isBlank() || value.matches("no?")) {
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
            List<String> allOutput = new ArrayList<>(1024);

            // If there was an error exit value, search to see if we should treat it as success
            if (errorMatchForSuccess != null) {
                // output any errors we got
                while (errorReader.ready() && (line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }

                // Process the data we had, too
                while ((line = outputReader.readLine()) != null) {
                    allOutput.add(line);
                    if (config.isDisplayExecutionOutput()) {
                        System.out.println(line);
                    }
                    if (!foundDesiredValue && errorMatchForSuccess.matcher(line).find()) {
                        System.out.println("Found desired line: " + errorMatchForSuccess);
                        foundDesiredValue = true;
                    }
                    output.add(line);
                }
            }

            int processResult = process.waitFor();

            if (processResult != 0 && !foundDesiredValue) {
                for (String errLine: allOutput) {
                    System.err.println(errLine);
                }
                throw new RuntimeException(String.format("Could not find desired value in output: [%s] status code: %d",
                        errorMatchForSuccess, processResult));
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
            angel.setupDependencyManagement();
            angel.process();
            angel.exclusionReduction();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
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
