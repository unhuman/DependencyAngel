package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.dependency.Dependency;

import java.util.ArrayList;
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
    protected static final String AND_LINE = "and";

    private Mode mode;
    private List<DependencyConflict> dependencyConflicts;

    private ConvergenceParser() {
        mode = Mode.LOOKING;
        dependencyConflicts = new ArrayList<>();
    }

    public static ConvergenceParser parse(List<String> data) {
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

    protected void processLine(String line) {
        switch (mode) {
            case LOOKING:
                Matcher matcher = CONVERGE_ERROR.matcher(line);
                if (matcher.matches()) {
                    Dependency dependency = new Dependency(matcher.group(0));
                    mode = Mode.FOUND_DEPENDENCY;
                }
                break;
            case FOUND_DEPENDENCY:
                // This line is the start of a dependency - we know this is the application

                // Populate data

                mode = Mode.PROCESS_CHILDREN;
                break;
            case PROCESS_CHILDREN:
                // Check if we found a blank line (end of this conflict)
                if (line.isBlank()) {
                    mode = Mode.LOOKING;
                    break;
                }

                // Check if we found an "and" line
                if (AND_LINE.equals(line)) {
                    mode = Mode.FOUND_DEPENDENCY;
                    break;
                }

                // TODO: Deal with indentation tracking and populate data

                break;
        }
    }
}
