package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.dependency.Dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DependencyConflict {
    private Dependency dependency;
    private List<DependencyConflictData> conflictHierarchy;

    public DependencyConflict(Dependency dependency) {
        this.dependency = dependency;
        this.conflictHierarchy = new ArrayList<>();
    }

    public Dependency getDependency() {
        return dependency;
    }

    public List<DependencyConflictData> getConflictHierarchy() {
        return Collections.unmodifiableList(conflictHierarchy);
    }

    public void addConflict(DependencyConflictData conflictData) {
        this.conflictHierarchy.add(conflictData);
    }
}
