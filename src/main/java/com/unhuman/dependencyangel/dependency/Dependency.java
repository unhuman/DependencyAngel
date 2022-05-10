package com.unhuman.dependencyangel.dependency;

import com.unhuman.dependencyangel.versioning.Version;

import java.util.ArrayList;
import java.util.List;

public class Dependency {
    private String group;
    private String artifact;
    private String classifier;
    private String scope;
    private String type;
    private Version version;

    private List<Dependency> exclusions = new ArrayList<>();

    public Dependency(String data) {
        classifier = null;
        String[] details = data.split(":");

        if (details.length == 3) {
            int i = 0;
            group = details[i++];
            artifact = details[i++];
            type = null;
            version = new Version(details[i++]);
        } else if (details.length < 4) {
                throw new RuntimeException("Invalid Dependency Data: " + String.join(":", data));
        } else {
            int i = 0;
            group = details[i++];
            artifact = details[i++];
            type = details[i++];
            version = new Version(details[i++]);
            if (details.length >= 5) {
                scope = details[i++];
            }
        }
    }

    protected Dependency(Dependency dependency) {
        this.group = dependency.getGroup();
        this.artifact = dependency.getArtifact();
        this.classifier = dependency.getClassifier();
        this.type = dependency.getType();
        this.scope = dependency.getScope();
        this.version = dependency.getVersion();
    }

    public Dependency(String groupId, String artifactId) {
        this(groupId, artifactId, null, null, null, null);
    }

    public Dependency(String groupId, String artifactId, String type, Version version,
                      String scope, String classifier) {
        if (groupId == null || groupId.isBlank()) {
            throw new RuntimeException("Missing dependency groupId");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new RuntimeException("Missing dependency artifactId");
        }
        this.group = groupId;
        this.artifact = artifactId;
        this.type = type;
        this.version = version;
        this.scope = scope;
        this.classifier = classifier;
    }

    public String getGroup() {
        return group;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getType() {
        return type;
    }

    public Version getVersion() {
        return version;
    }

    protected void setVersion(Version newVersion) {
        this.version = newVersion;
    }

    public String getScope() {
        return scope;
    }
    protected void setScope(String newScope) {
        this.scope = newScope;
    }

    public String getClassifier() {
        return classifier;
    }

    public boolean matchingArtifact(Dependency other) {
        return (this.getGroup().equals(other.getGroup()) && this.getArtifact().equals(other.getArtifact()));
    }

    public String getDisplayName() {
        return String.format("%s:%s", getGroup(), getArtifact());
    }

    public List<Dependency> getExclusions() {
        return exclusions;
    }

    public void setExclusions(List<Dependency> exclusions) {
        this.exclusions.addAll(exclusions);
    }
}
