package com.unhuman.dependencyresolver.utility;

import com.unhuman.dependencyresolver.dependency.Dependency;
import com.unhuman.dependencyresolver.tgf.TgfData;
import com.unhuman.dependencyresolver.tgf.TgfDependencyRelationship;
import com.unhuman.dependencyresolver.tree.DependencyNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyHelper {
    private TgfData tgfData;

    // Map for quick lookup to find an item in a relationship
    private Map<String, DependencyNode> dataMap = new HashMap<>();

    DependencyHelper(TgfData tgfData) {
        this.tgfData = tgfData;
    }

    public static DependencyNode convertTgfData(TgfData tgfData) {
        DependencyHelper helper = new DependencyHelper(tgfData);
        return helper.convert();
    }

    DependencyNode convert() {
        // iterate through all the relationships to build the tree
        List<TgfDependencyRelationship> tgfRelationships = tgfData.getRelationships();
        for (TgfDependencyRelationship tgfRelationship : tgfRelationships) {
            String relationshipParent = tgfRelationship.getParent();
            String relationshipChild = tgfRelationship.getChild();

            // ensure parent and child exist
            DependencyNode parent = ensureNodeExists(relationshipParent);
            DependencyNode child = ensureNodeExists(relationshipChild);

            // set up the relationships
            parent.addChild(child);
            child.setParent(parent);
        }

        // now extract the root
        return dataMap.get(tgfData.getRootNode());
    }

    DependencyNode ensureNodeExists(String nodeName) {
        if (!dataMap.containsKey(nodeName)) {
            DependencyNode created = new DependencyNode(null, tgfData.getDependencies().get(nodeName));
            dataMap.put(nodeName, created);
        }

        return dataMap.get(nodeName);
    }
}
