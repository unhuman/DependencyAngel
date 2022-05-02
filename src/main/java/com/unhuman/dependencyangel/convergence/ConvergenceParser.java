package com.unhuman.dependencyangel.convergence;

import com.unhuman.dependencyangel.dependency.Dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConvergenceParser {
    private enum Mode {
        LOOKING,
        FOUND_DEPENDENCY,
        PROCESS_CHILDREN
    }
    public static final Pattern CONVERGE_ERROR = Pattern.compile(
            "Dependency convergence error for (.*?) paths to dependency are:");
    public static final Pattern CONVERGE_LINE = Pattern.compile(
            "(\\s*)(?:[^\\sa-z]*)(.*)", Pattern.CASE_INSENSITIVE);
    protected static final String AND_LINE = "and";

    private Mode mode;
    private String indentStep = null;
    private List<DependencyConflict> dependencyConflicts;

    private ConvergenceParser() {
        mode = Mode.LOOKING;
        dependencyConflicts = new ArrayList<>();
    }

    public static ConvergenceParser from(List<String> data) {
        ConvergenceParser parser = new ConvergenceParser();
        parser.process(data);
        return parser;
    }

    protected void process(List<String> data) {
        int lineNum = 0;
        try {
            for (String line: data) {
                ++lineNum;
                processLine(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error Line: " + lineNum + ": " + e.getMessage(), e);
        }

        if (!Mode.LOOKING.equals(mode)) {
            throw new RuntimeException("Convergence data incomplete");
        }
    }

    public List<DependencyConflict> getDependencyConflicts() {
        return Collections.unmodifiableList(dependencyConflicts);
    }

    protected void processLine(String line) {
        Matcher matcher;
        DependencyConflictData conflict;
        switch (mode) {
            case LOOKING:
                matcher = CONVERGE_ERROR.matcher(line);
                if (matcher.matches()) {
                    Dependency dependencyConflict = new Dependency(matcher.group(1));
                    dependencyConflicts.add(new DependencyConflict(dependencyConflict));
                    mode = Mode.FOUND_DEPENDENCY;
                }
                break;
            case FOUND_DEPENDENCY:
                matcher = CONVERGE_LINE.matcher(line);
                if (!matcher.matches()) {
                    throw new RuntimeException("Didn't find expected convergence data: " + line);
                }
                conflict = new DependencyConflictData(null, new Dependency(matcher.group(2)));
                dependencyConflicts.get(dependencyConflicts.size() - 1).addConflict(conflict);

                mode = Mode.PROCESS_CHILDREN;
                break;
            case PROCESS_CHILDREN:
                // Check if we found a blank line, closing bracket (end of this conflict)
                if (line.isBlank() || line.trim().equals("]")) {
                    mode = Mode.LOOKING;
                    break;
                }

                // Check if we found an "and" (and or comma) line
                if (AND_LINE.equals(line)|| line.trim().equals(",") ) {
                    mode = Mode.FOUND_DEPENDENCY;
                    break;
                }

                matcher = CONVERGE_LINE.matcher(line);
                if (!matcher.matches()) {
                    throw new RuntimeException("Expected convergence information, not: " + line);
                }

                // First time we find an indent, keep track.  This will help with figuring
                if (indentStep == null && matcher.group(1).length() > 0) {
                    indentStep = matcher.group(1);
                }

                // determine the indent level
                int indentLevel = matcher.group(1).length() / indentStep.length();

                // find the parent out of the most recent conflicts

                DependencyConflict currentConflict = dependencyConflicts.get(dependencyConflicts.size() - 1);
                List<DependencyConflictData> currentHierarchy = currentConflict.getConflictHierarchy();

                DependencyConflictData parent = currentHierarchy.get(currentHierarchy.size() - 1)
                        .findFindLastChild(indentLevel - 1);

                // create a dependency
                Dependency newDependency = new Dependency(matcher.group(2));
                conflict = new DependencyConflictData(parent, newDependency);
                parent.addChild(conflict);

                // Update the version of the item in the conflict to be latest
                currentConflict.updateConflictInfo(newDependency);

                break;
        }
    }
}
