package com.unhuman.dependencyangel.tree;

import com.unhuman.dependencyangel.dependency.Dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DependencyNode extends Dependency {
    DependencyNode parent; // TODO: Do we need this?
    List<DependencyNode> children;
    public DependencyNode(DependencyNode parent, Dependency dependency) {
        super(dependency);
        this.parent = parent;
        children = new ArrayList<>();
    }

    public void setParent(DependencyNode parent) {
        this.parent = parent;
    }

    public DependencyNode getParent() {
        return parent;
    }

    public void addChild(DependencyNode dependency) {
        children.add(dependency);
    }

    public List<DependencyNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
