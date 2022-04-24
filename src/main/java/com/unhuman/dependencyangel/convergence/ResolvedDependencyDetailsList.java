package com.unhuman.dependencyangel.convergence;

import com.unhuman.dependencyangel.versioning.Version;

import java.util.ArrayList;

public class ResolvedDependencyDetailsList extends ArrayList<ResolvedDependencyDetails> {
    /**
     * Adds an item if not a conflict.  If item is not added, we know this is a duplicate inclusion
     * which should be transitive exclusion and an explicit (or some other existing) dependency
     * should be ensured to cover this
     *
     * @param resolvedDependencyDetails element whose presence in this collection is to be ensured
     * @return whether or not added
     */
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

                // We add this dependency to the existing item so that it knows it has multiple
                currentItem.addAll(resolvedDependencyDetails);

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
        return this.get(0).get(0).getArtifact();
    }
}
