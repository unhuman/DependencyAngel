package com.unhuman.dependencyangel.versioning;

import com.unhuman.dependencyangel.dependency.ArtifactHelper;

import java.util.Set;

public class VersionHelper {
    private Set<String> nonSemanticVersions;

    public VersionHelper(Set<String> nonSemanticVersions) {
        this.nonSemanticVersions = nonSemanticVersions;
    }

    public boolean useSemanticVersioning(String groupId, String artifactId) {
        return useSemanticVersioning(ArtifactHelper.getArtifactIdGroupIdString(groupId, artifactId));
    }

    public boolean useSemanticVersioning(String groupIdArtifactId) {
        return !nonSemanticVersions.contains(groupIdArtifactId);
    }
}
