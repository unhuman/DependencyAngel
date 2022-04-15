package com.unhuman.dependencyresolver.tgf;

import com.unhuman.dependencyresolver.dependency.Dependency;

import java.util.*;

public class TgfData {
    private String parentNode;
    private Map<String, Dependency> dependencies;
    private Map<String, List<TgfDependencyRelationship>> relationships;

    public TgfData() {
        dependencies = new HashMap<>();
        // order of insertion matters
        relationships = new LinkedHashMap<>();
    }

    void addDependency(String id, Dependency dependency) {
        // First id provided is the parent
        if (parentNode == null) {
            parentNode = id;
        }

        dependencies.put(id, dependency);
    }

    void addRelationship(TgfDependencyRelationship relationship) {
        // Initialize sub-data, if necessary
        if (!relationships.containsKey(relationship.getParent())) {
            relationships.put(relationship.getParent(), new ArrayList<>());
        }
        relationships.get(relationship.getParent()).add(relationship);
    }

    public String getParentNode() {
        return parentNode;
    }

    public Map<String, Dependency> getDependencies() {
        return dependencies;
    }

    public Map<String, List<TgfDependencyRelationship>> getRelationships() {
        return relationships;
    }
}
