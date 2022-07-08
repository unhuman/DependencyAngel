package com.unhuman.dependencyangel.convergence;

import com.unhuman.dependencyangel.dependency.Dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DependencyConflict extends Dependency {
    // Hierarchy for what scope to choose.  Items to the left are higher priority than on the right.
    private static final List<String> SCOPE =
            Arrays.asList(new String[]{"compile", "provided", "system", "runtime", "import", "test"});

    private List<DependencyConflictData> conflictHierarchy;

    public DependencyConflict(Dependency dependency) {
        super(dependency);
        this.conflictHierarchy = new ArrayList<>();
    }

    public List<DependencyConflictData> getConflictHierarchy() {
        return Collections.unmodifiableList(conflictHierarchy);
    }

    public void addConflict(DependencyConflictData conflictData) {
        this.conflictHierarchy.add(conflictData);
    }

    public void updateConflictInfo(Dependency newDependency) {
        if (getGroupId().equals(newDependency.getGroupId())
                && getArtifactId().equals(newDependency.getArtifactId())) {
            if (newDependency.getVersion().compareTo(getVersion()) > 0) {
                setVersion(newDependency.getVersion());
            }

            if (SCOPE.indexOf(newDependency.getScope()) < SCOPE.indexOf(getScope())) {
                setScope(newDependency.getScope());
            }
        }
    }

    public boolean containsDependency(Dependency dependency) {
        for (DependencyConflictData conflictData: conflictHierarchy) {
            if (conflictData.containsDependency(dependency)) {
                return true;
            }
        }
        return false;
    }
}
