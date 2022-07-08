package com.unhuman.dependencyangel.convergence;

import com.unhuman.dependencyangel.dependency.Dependency;
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
        if (getGroupId().equals(dependency.getGroupId()) && getArtifactId().equals(dependency.getArtifactId())) {
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

        DependencyConflictData initialDependency = null;
        switch (children.size()) {
            case 0:
                // This could happen with a circular dependency
                return null;
            case 1:
                initialDependency = children.get(0);
                // let's find all the dependencies
                return new ResolvedDependencyDetails(initialDependency, getEndChildren(initialDependency));
            default:
                throw new RuntimeException("Expected only a single child at parent level");
        }
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