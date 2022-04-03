package com.unhuman.dependencyresolver.tgf;

public class DependencyInfo {
    private String group;
    private String artifact;
    private String format;
    private Version version;
    private String scope;

    public DependencyInfo(String data) {
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
