package com.unhuman.dependencyangel;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorableAngelConfigData {
    private static final String ANGEL_CONFIG_FILE = ".angel.conf";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private String path = null;
    private Set<String> bannedDependencies;
    private Set<String> preserveExclusions;
    private List<String> managedVersions;
    private List<String> managedDependencies;
    private boolean dirty = true;

    protected StorableAngelConfigData() {
        this.bannedDependencies = Collections.emptySet();
        this.preserveExclusions = Collections.emptySet();
        this.managedVersions = new ArrayList<>();
        this.managedDependencies = new ArrayList<>();
    }

    private StorableAngelConfigData(StorableAngelConfigData copy) {
        this.bannedDependencies = copy.bannedDependencies;
        this.preserveExclusions = copy.preserveExclusions;
        this.managedVersions = copy.managedVersions;
        this.managedDependencies = copy.managedDependencies;
    }

    protected void setup(Namespace ns, String projectDirectory) {
        path = projectDirectory;
        bannedDependencies = getDependenciesSet(ns, "banned");
        preserveExclusions = getDependenciesSet(ns, "preserveExclusions");

        StorableAngelConfigData fileConfig = loadConfig(projectDirectory);
        if (fileConfig != null) {
            bannedDependencies.addAll(fileConfig.getBannedDependencies());
            preserveExclusions.addAll(fileConfig.getPreserveExclusions());
            managedVersions.addAll(fileConfig.getManagedVersions());
            managedDependencies.addAll(fileConfig.getManagedDependencies());
        }

        // TODO: Only update if there's a change
        writeConfig();
    }

    public Set<String> getBannedDependencies() {
        return Collections.unmodifiableSet(bannedDependencies);
    }

    public Set<String> getPreserveExclusions() {
        return Collections.unmodifiableSet(preserveExclusions);
    }

    public void clearManagedVersions() {
        if (this.managedVersions.size() > 0) {
            dirty = true;
            this.managedVersions = new ArrayList<>();
        }
    }

    public void addManagedVersion(String managedVersion) {
        dirty = true;
        this.managedVersions.add(managedVersion);
    }

    public List<String> getManagedVersions() {
        return managedVersions;
    }

    public void clearManagedDependencies() {
        if (this.managedDependencies.size() > 0) {
            dirty = true;
            this.managedDependencies = new ArrayList<>();
        }
    }

    public void addManagedDependency(String manageDependency) {
        dirty = true;
        this.managedDependencies.add(manageDependency);
    }

    public List<String> getManagedDependencies() {
        return managedDependencies;
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

    private void writeConfig() {
        if (path == null) {
            throw new RuntimeException("No path");
        }
        String configFilePath = getConfigFilePath(path);
        try {
            // Ensure we only write the data in this object
            StorableAngelConfigData writeData = new StorableAngelConfigData(this);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(configFilePath), writeData);
            dirty = false;
        } catch (Exception e) {
            System.out.println("Could not write config: " + configFilePath + ": " + e.getMessage());
        }
    }

    public void updateConfig() {
        if (dirty) {
            writeConfig();
        }
    }

    public static StorableAngelConfigData loadConfig(String projectDirectory) {
        String configFilePath = getConfigFilePath(projectDirectory);
        try {
            File configFile = new File(configFilePath);
            if (!configFile.exists()) {
                return null;
            }
            if (!configFile.isFile()) {
                throw new RuntimeException("Config file is not a file: " + configFile.getCanonicalPath());
            }

            StorableAngelConfigData item = OBJECT_MAPPER.readValue(configFile, StorableAngelConfigData.class);
            return item;
        } catch (Exception e) {
            System.out.println("Could not load config: " + configFilePath + ": " + e.getMessage());
            return null;
        }
    }

    private static String getConfigFilePath(String projectDirectory) {
        return projectDirectory + File.separatorChar + ANGEL_CONFIG_FILE;
    }
}
