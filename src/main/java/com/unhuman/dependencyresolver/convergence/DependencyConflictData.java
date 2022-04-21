package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.dependency.Dependency;
import java.util.ArrayList;
import java.util.List;

public class DependencyConflictData extends Dependency {
    private DependencyConflictData parent;
    private List<DependencyConflictData> children;

    public DependencyConflictData(DependencyConflictData parent, Dependency dependency) {
        super(dependency);
        this.parent = parent;
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

    public ResolvedDependencyDetails getEndDependencyInfo() {
        if (parent != null) {
            throw new RuntimeException("Illegal use of this method - top level only");
        }

        if (children.size() != 1) {
            throw new RuntimeException("Expected only a single child at parent level");
        }

        // let's find all the dependencies
        DependencyConflictData initalDependency = children.get(0);
        return new ResolvedDependencyDetails(initalDependency, getEndChildren(initalDependency));
    }

    private List<Dependency> getEndChildren(DependencyConflictData data) {
        List<Dependency> endChildren = new ArrayList<>();
        if (data.children.size() == 0) {
            endChildren.add(data);
        } else {
            for (DependencyConflictData child: data.children) {
                endChildren.addAll(getEndChildren(child));
            }
        }

        return endChildren;
    }
}