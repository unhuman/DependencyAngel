package com.unhuman.dependencyangel.dependency;

public class ArtifactHelper {
    public static String getArtifactIdGroupIdString(String artifactId, String groupId) {
        return String.format("%s:%s", artifactId, groupId);
    }
}
