package com.unhuman.dependencyangel.convergence;

import com.unhuman.dependencyangel.dependency.Dependency;
import com.unhuman.dependencyangel.versioning.Version;

import java.util.ArrayList;
import java.util.List;

public class ResolvedDependencyDetails extends ArrayList<Dependency> {
    private Dependency initialDependency;

    public ResolvedDependencyDetails(Dependency initialDependency, List<Dependency> endDependencies) {
        this.initialDependency = initialDependency;
        super.addAll(endDependencies);
    }

    public Dependency getInitialDependency() {
        return initialDependency;
    }

    public boolean isExplicitDependency() {
        return (this.size() == 1 && initialDependency.equals(this.get(0)));
    }

    @Override
    public boolean add(Dependency data) {
        throw new RuntimeException("Don't use this add");
    }

    @Override
    public void add(int index, Dependency element) {
        throw new RuntimeException("Don't use this add");
    }

    public boolean hasMultipleDependencies() {
        return (this.size() > 1);
    }

    public boolean needsExclusion(Version version) {
        return (hasMultipleDependencies() || version.compareTo(this.getLatestVersion()) > 0);
    }

    public String getResolvedType() {
        String type = null;
        for (Dependency data: this) {
            if (type == null) {
                type = data.getType();
            } else if (!type.equals(data.getType())) {
                return null;
            }
        }
        return type;
    }

    public String getResolvedScope() {
        String scope = null;
        for (Dependency data: this) {
            if (scope == null) {
                scope = data.getScope();
            } else if (!scope.equals(data.getScope())) {
                return null;
            }
        }
        return scope;
    }

    public String getResolvedClassifier() {
        String classifier = null;
        for (Dependency data: this) {
            if (classifier == null) {
                classifier = data.getClassifier();
            } else if (!classifier.equals(data.getClassifier())) {
                return null;
            }
        }
        return classifier;
    }

    public Version getLatestVersion() {
        Version version = null;
        for (Dependency data: this) {
            if (version == null) {
                version = data.getVersion();
            } else if (data.getVersion().compareTo(version) > 0) {
                version = data.getVersion();
            }
        }
        return version;
    }

    public List<Version> getAllVersions() {
        List<Version> allVersions = new ArrayList<>(this.size());
        for (Dependency data: this) {
            if (!allVersions.contains(data.getVersion())) {
                allVersions.add(data.getVersion());
            }
        }
        return allVersions;
    }

}
