package com.unhuman.dependencyangel.versioning;

import java.util.Set;

public class VersionHelper {
    private Set<String> nonSemanticVersions;

    public VersionHelper(Set<String> nonSemanticVersions) {
        this.nonSemanticVersions = nonSemanticVersions;
    }

    public boolean useSemanticVersioning(String groupId, String artifactId) {
        return useSemanticVersioning(groupId + ":" + artifactId);
    }

    public boolean useSemanticVersioning(String groupIdArtifactId) {
        return !nonSemanticVersions.contains(groupIdArtifactId);
    }
}
