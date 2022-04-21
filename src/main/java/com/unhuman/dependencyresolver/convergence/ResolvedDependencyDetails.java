package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.dependency.Dependency;
import com.unhuman.dependencyresolver.versioning.Version;

import java.util.ArrayList;
import java.util.List;

public class ResolvedDependencyDetails extends ArrayList<Dependency> {
    Dependency initialDependency;

    public ResolvedDependencyDetails(Dependency initialDependency, List<Dependency> endDependencies) {
        this.initialDependency = initialDependency;
        super.addAll(endDependencies);
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
}
