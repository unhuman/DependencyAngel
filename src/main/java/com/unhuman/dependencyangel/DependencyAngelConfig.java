package com.unhuman.dependencyangel;

import java.util.HashMap;
import java.util.Map;

public class DependencyAngelConfig {
    private String directory;
    private Map<String, String> environmentVars;
    private boolean skipPrompts;
    private boolean cleanOnly;

    public DependencyAngelConfig(String directory) {
        this.directory = directory;
        this.environmentVars = new HashMap<>();
        this.skipPrompts = false;
        this.cleanOnly = false;
    }

    public String getDirectory() {
        return directory;
    }

    public Map<String, String> getEnvironmentVars() {
        return environmentVars;
    }

    public void addEnvironmentVars(Map<String, String> environmentVars) {
        this.environmentVars.putAll(environmentVars);
    }

    public boolean isSkipPrompts() {
        return skipPrompts;
    }

    public void setSkipPrompts(boolean skipPrompts) {
        this.skipPrompts = skipPrompts;
    }

    public boolean isCleanOnly() {
        return cleanOnly;
    }

    public void setCleanOnly(boolean cleanOnly) {
        this.cleanOnly = cleanOnly;
    }
}
