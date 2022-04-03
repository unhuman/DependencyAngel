package com.unhuman.dependencyresolver.tgf;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class TgfData {
    private enum Mode { DATA, RELATIONSHIPS }
    private Mode mode;

    private String parentNode;
    private Map<String, DependencyInfo> dependencies;
    private Map<String, List<DependencyRelationship>> relationships;

    public TgfData(String filename) throws FileNotFoundException {
        this(new Scanner(new File(filename)));
    }

    protected TgfData(Scanner scanner) {
        dependencies = new HashMap<>();
        relationships = new HashMap<>();

        int lineNum = 0;
        try {
            mode = Mode.DATA;
            while (scanner.hasNextLine()) {
                ++lineNum;
                String line = scanner.nextLine();
                processLine(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error Line: " + lineNum + ": " + e.getMessage(), e);
        }
    }

    protected void processLine(String line) {
        if (line.equals("#")) {
            mode = Mode.RELATIONSHIPS;
            return;
        }

        switch (mode) {
            case DATA:
                String[] item = line.split("\\s+");
                if (item.length != 2) {
                    throw new RuntimeException("Invalid Data Line: " + line);
                }

                String id = item[0];
                if (parentNode == null) {
                    parentNode = id;
                }
                DependencyInfo dependencyInfo = new DependencyInfo(item[1]);
                dependencies.put(id, dependencyInfo);
                break;
            case RELATIONSHIPS:
                DependencyRelationship relationship = new DependencyRelationship(line);
                if (!relationships.containsKey(relationship.getParent())) {
                    relationships.put(relationship.getParent(), new ArrayList<>());
                }
                relationships.get(relationship.getParent()).add(relationship);
                break;
        }
    }
}
