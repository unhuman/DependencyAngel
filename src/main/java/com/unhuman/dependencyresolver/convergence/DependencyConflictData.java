package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.dependency.Dependency;

import java.util.ArrayList;
import java.util.List;

public class DependencyConflictData {
    private Dependency dependency;
    private DependencyConflictData parent;
    private List<DependencyConflictData> children;

    public DependencyConflictData(DependencyConflictData parent, Dependency dependency) {
        this.parent = null;
        this.dependency = dependency;
        this.children = new ArrayList<>();
    }

    public void addChild(DependencyConflictData child) {
        this.children.add(child);
    }

    DependencyConflictData findFindLastChild(int nestedLevel) {
        if (nestedLevel > 0) {
            return children.get(children.size() - 1).findFindLastChild(nestedLevel - 1);
        }

        return this;
    }

    public Dependency getDependency() {
        return dependency;
    }
}
