package com.unhuman.dependencyangel;

import net.sourceforge.argparse4j.inf.Namespace;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StorableAngelConfigData {
    private Set<String> bannedDependencies;
    private Set<String> preserveExclusions;

    protected StorableAngelConfigData() {
        this.bannedDependencies = Collections.emptySet();
        this.preserveExclusions = Collections.emptySet();
    }

    protected void setup(Namespace ns) {
        bannedDependencies = getDependenciesSet(ns, "banned");
        preserveExclusions = getDependenciesSet(ns, "preserveExclusions");
    }

    public Set<String> getBannedDependencies() {
        return Collections.unmodifiableSet(bannedDependencies);
    }

    public Set<String> getPreserveExclusions() {
        return Collections.unmodifiableSet(preserveExclusions);
    }

    private Set<String> getDependenciesSet(Namespace ns, String itemExtract) {
        String data = ns.getString(itemExtract);
        Set<String> dependencies = new HashSet<>();
        if (data != null) {
            String[] items = data.split("[,]+");
            for (String item : items) {
                String[] parts = item.split(":");
                if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    dependencies.add(item);
                    continue;
                }
                throw new RuntimeException("Invalid config item: " + itemExtract + " - " + data);
            }
        }
        return dependencies;
    }
}
