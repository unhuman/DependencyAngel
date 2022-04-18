package com.unhuman.dependencyresolver.dependency;

import com.unhuman.dependencyresolver.versioning.Version;

public class Dependency {
    private String group;
    private String artifact;
    private String format;
    private Version version;
    private String scope;

    public Dependency(String data) {
        String[] details = data.split(":");
        if (details.length < 4) {
            throw new RuntimeException("Invalid Dependency Data: " + String.join(":", data));
        }

        int i = 0;
        group = details[i++];
        artifact = details[i++];
        format = details[i++];
        version = new Version(details[i++]);
        if (details.length >= 5) {
            scope = details[i++];
        }
    }

    protected Dependency(Dependency dependency) {
        this.group = dependency.getGroup();
        this.artifact = dependency.getArtifact();
        this.format = dependency.getFormat();
        this.version = dependency.getVersion();
        this.scope = dependency.getScope();
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

    public String getScope() {
        return scope;
    }
}
