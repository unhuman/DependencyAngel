package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.dependency.Dependency;

import java.util.ArrayList;
import java.util.List;

public class DependencyConflictData extends Dependency {
    private DependencyConflictData parent;
    private List<DependencyConflictData> children;

    public DependencyConflictData(DependencyConflictData parent, Dependency dependency) {
        super(dependency);
        this.parent = null;
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

    public boolean containsDependency(Dependency dependency) {
        if (getGroup().equals(dependency.getGroup()) && getArtifact().equals(dependency.getArtifact())) {
            return true;
        }

        for (DependencyConflictData child : children) {
            if (child.containsDependency(dependency)) {
                return true;
            }
        }

        return false;
    }
}