package com.unhuman.dependencyangel.tgf;

import com.unhuman.dependencyangel.dependency.Dependency;

import java.util.*;

public class TgfData {
    private String rootNode;
    private Map<String, Dependency> dependencies;
    private List<TgfDependencyRelationship> relationships;

    public TgfData() {
        dependencies = new HashMap<>();
        // order of insertion matters
        relationships = new ArrayList<>();
    }

    void addDependency(String id, Dependency dependency) {
        // First id provided is the parent
        if (rootNode == null) {
            rootNode = id;
        }

        dependencies.put(id, dependency);
    }

    void addRelationship(TgfDependencyRelationship relationship) {
        relationships.add(relationship);
    }

    public String getRootNode() {
        return rootNode;
    }

    public Map<String, Dependency> getDependencies() {
        return Collections.unmodifiableMap(dependencies);
    }

    public List<TgfDependencyRelationship> getRelationships() {
        return Collections.unmodifiableList(relationships);
    }
}
