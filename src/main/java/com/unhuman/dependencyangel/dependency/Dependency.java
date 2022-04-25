package com.unhuman.dependencyangel.dependency;

import com.unhuman.dependencyangel.versioning.Version;

public class Dependency {
    private String group;
    private String artifact;
    private String format;
    private Version version;
    private String scope;

    public Dependency(String data) {
        String[] details = data.split(":");

        if (details.length == 3) {
            int i = 0;
            group = details[i++];
            artifact = details[i++];
            format = null;
            version = new Version(details[i++]);
        } else if (details.length < 4) {
                throw new RuntimeException("Invalid Dependency Data: " + String.join(":", data));
        } else {
            int i = 0;
            group = details[i++];
            artifact = details[i++];
            format = details[i++];
            version = new Version(details[i++]);
            if (details.length >= 5) {
                scope = details[i++];
            }
        }
    }

    protected Dependency(Dependency dependency) {
        this.group = dependency.getGroup();
        this.artifact = dependency.getArtifact();
        this.format = dependency.getFormat();
        this.version = dependency.getVersion();
        this.scope = dependency.getScope();
    }

    public Dependency(String groupId, String artifactId, String format, Version version, String scope) {
        if (groupId == null || groupId.isBlank()) {
            throw new RuntimeException("Missing dependency groupId");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new RuntimeException("Missing dependency artifactId");
        }
        this.group = groupId;
        this.artifact = artifactId;
        this.format = format;
        this.version = version;
        this.scope = scope;
    }

    public String getGroup() {
        return group;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getFormat() {
        return format;
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

    public boolean matchingArtifact(Dependency other) {
        return (this.getGroup().equals(other.getGroup()) && this.getArtifact().equals(other.getArtifact()));
    }

    public String getDisplayName() {
        return String.format("%s:%s", getGroup(), getArtifact());
    }
}
