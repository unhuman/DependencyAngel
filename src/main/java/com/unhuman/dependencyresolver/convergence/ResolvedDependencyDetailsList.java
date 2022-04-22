package com.unhuman.dependencyresolver.convergence;

import com.unhuman.dependencyresolver.versioning.Version;

import java.util.ArrayList;

public class ResolvedDependencyDetailsList extends ArrayList<ResolvedDependencyDetails> {
    /**
     * Indicate if there is an existing explicit dependency
     * @return
     */
    public boolean hasExplicitDependency() {
        for (ResolvedDependencyDetails resolvedDependencyDetails : this) {
            if (resolvedDependencyDetails.isExplicitDependency()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean add(ResolvedDependencyDetails resolvedDependencyDetails) {
        // don't allow adding a duplicate start / end item
        for (ResolvedDependencyDetails currentItem: this) {
            if (currentItem.getInitialDependency().getGroup()
                    .equals(resolvedDependencyDetails.getInitialDependency().getGroup())
                && currentItem.getInitialDependency().getArtifact()
                    .equals(resolvedDependencyDetails.getInitialDependency().getArtifact())
                && currentItem.get(0).getGroup()
                    .equals(resolvedDependencyDetails.get(0).getGroup())
                && currentItem.get(0).getArtifact()
                    .equals(resolvedDependencyDetails.get(0).getArtifact())) {
                return false;
            }
        }
        return super.add(resolvedDependencyDetails);
    }

    /**
     * gets the latest version that is used across all the dependencies
     * @return
     */
    public Version getLatestVersion() {
        Version latestVersion = null;
        for (ResolvedDependencyDetails resolvedDependencyDetails : this) {
            Version dependencyLatestVersion = resolvedDependencyDetails.getLatestVersion();
            if (latestVersion == null || dependencyLatestVersion.compareTo(latestVersion) > 0) {
                latestVersion = dependencyLatestVersion;
            }
        }
        return latestVersion;
    }

    /**
     * Indicate if there needs to be an existing explicit dependency
     * @return
     */
    public boolean needsExplicitDependency() {
        for (ResolvedDependencyDetails resolvedDependencyDetails : this) {
            if (resolvedDependencyDetails.isExplicitDependency()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the scope of all the nested children, or null if not consistent
     * @return
     */
    public String getResolvedScope() {
        String scope = null;
        for (ResolvedDependencyDetails resolvedDependencyDetails: this) {
            if (scope == null) {
                scope = resolvedDependencyDetails.getResolvedScope();
            } else if (!scope.equals(resolvedDependencyDetails.getResolvedScope())) {
                return null;
            }
        }
        return scope;
    }

    public String getGroup() {
        return this.get(0).get(0).getGroup();
    }

    public String getArtifact() {
        return this.get(0).get(0).getGroup();
    }
}
