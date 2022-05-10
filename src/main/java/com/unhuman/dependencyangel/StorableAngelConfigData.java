package com.unhuman.dependencyangel;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StorableAngelConfigData {
    private static final String ANGEL_CONFIG_FILE = ".angel.conf";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private Set<String> bannedDependencies;
    private Set<String> preserveExclusions;

    protected StorableAngelConfigData() {
        this.bannedDependencies = Collections.emptySet();
        this.preserveExclusions = Collections.emptySet();
    }

    private StorableAngelConfigData(StorableAngelConfigData copy) {
        this.bannedDependencies = copy.bannedDependencies;
        this.preserveExclusions = copy.preserveExclusions;
    }

    protected void setup(Namespace ns, String projectDirectory) {
        bannedDependencies = getDependenciesSet(ns, "banned");
        preserveExclusions = getDependenciesSet(ns, "preserveExclusions");

        StorableAngelConfigData fileConfig = loadConfig(projectDirectory);
        if (fileConfig != null) {
            bannedDependencies.addAll(fileConfig.getBannedDependencies());
            preserveExclusions.addAll(fileConfig.getPreserveExclusions());
        }

        // TODO: Only update if there's a change
        writeConfig(projectDirectory);
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

    public void writeConfig(String projectDirectory) {
        String configFilePath = getConfigFilePath(projectDirectory);
        try {
            // Ensure we only write the data in this object
            StorableAngelConfigData writeData = new StorableAngelConfigData(this);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(configFilePath), writeData);
        } catch (Exception e) {
            System.out.println("Could not write config: " + configFilePath + ": " + e.getMessage());
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