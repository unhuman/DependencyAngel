package com.unhuman.dependencyangel.convergence;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConvergenceParserTest {
    ClassLoader classLoader = this.getClass().getClassLoader();

    @Test
    public void testReadSingleFileWithClassLoader() throws IOException, URISyntaxException {
        List<String> strings = readFileToList("analyzeSingle.txt");
        ConvergenceParser parser = ConvergenceParser.from(strings);
        List<DependencyConflict> conflicts = parser.getDependencyConflicts();
        assertEquals(1, conflicts.size());

        DependencyConflict conflict = conflicts.get(0);
        assertEquals("org.jboss.logging", conflict.getGroupId());
        assertEquals("jboss-logging", conflict.getArtifactId());

        List<DependencyConflictData> conflictHierarchy = conflict.getConflictHierarchy();
        assertEquals(4, conflictHierarchy.size());

        DependencyConflictData conflictData;
        ResolvedDependencyDetails details;
        int conflictCounter = 0;

        // 1st item
        conflictData = conflictHierarchy.get(conflictCounter++);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        conflictData = conflictHierarchy.get(0);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        // 2nd item - same hierarchy as first
        conflictData = conflictHierarchy.get(conflictCounter++);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        conflictData = conflictHierarchy.get(0);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        // 3rd item - same hierarchy as first
        conflictData = conflictHierarchy.get(conflictCounter++);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        conflictData = conflictHierarchy.get(0);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        // 4th item - direct dependency
        conflictData = conflictHierarchy.get(conflictCounter++);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("org.jboss.logging", details.getInitialDependency().getGroupId());
        assertEquals("jboss-logging", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

        conflictData = conflictHierarchy.get(0);
        assertEquals("com.unhuman", conflictData.getGroupId());
        assertEquals("CouchbaseUI", conflictData.getArtifactId());

        details = conflictData.getEndDependencyInfo();
        assertEquals(1, details.size());
        assertEquals("com.codingrodent", details.getInitialDependency().getGroupId());
        assertEquals("jackson-json-crypto", details.getInitialDependency().getArtifactId());
        assertEquals("org.jboss.logging", details.get(0).getGroupId());
        assertEquals("jboss-logging", details.get(0).getArtifactId());

    }

    @Test
    public void testReadFullFileWithClassLoader() throws IOException, URISyntaxException {
        List<String> strings = readFileToList("analyzeFull.txt");
        ConvergenceParser parser = ConvergenceParser.from(strings);
        List<DependencyConflict> conflicts = parser.getDependencyConflicts();
        assertEquals(5, conflicts.size());

        DependencyConflict conflict = conflicts.get(0);
        assertEquals("org.slf4j", conflict.getGroupId());
        assertEquals("slf4j-api", conflict.getArtifactId());

        List<DependencyConflictData> conflictHierarchy = conflict.getConflictHierarchy();
        assertEquals(4, conflictHierarchy.size());

        // TODO
//        assertEquals("com.unhuman", conflictHierarchy.get(0).getGroup());
//        assertEquals("CouchbaseUI", conflictHierarchy.get(0).getArtifact());
//        assertEquals("com.codingrodent", conflictHierarchy.get(3).getGroup());
//        assertEquals("jackson-json-crypto", conflictHierarchy.get(3).getArtifact());
    }

    private List<String> readFileToList(String filename) throws IOException, URISyntaxException {
        String filepath = new File(classLoader.getResource(filename).getPath()).getAbsolutePath();
        try (Stream<String> lines = Files.lines(Paths.get(filepath))) {
            return lines.collect(Collectors.toList());
        }
    }
}
