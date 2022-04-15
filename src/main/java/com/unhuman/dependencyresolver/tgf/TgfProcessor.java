package com.unhuman.dependencyresolver.tgf;

import com.unhuman.dependencyresolver.dependency.Dependency;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class TgfProcessor {
    private enum Mode { DATA, RELATIONSHIPS }
    private Mode mode;
    TgfData tgfData;

    public TgfProcessor(String filename) throws FileNotFoundException {
        this(new Scanner(new File(filename)));
    }

    protected TgfProcessor(Scanner scanner) {
        tgfData = new TgfData();

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

    public TgfData getTgfData() {
        return tgfData;
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
                Dependency dependency = new Dependency(item[1]);
                tgfData.addDependency(id, dependency);
                break;
            case RELATIONSHIPS:
                TgfDependencyRelationship relationship = new TgfDependencyRelationship(line);
                tgfData.addRelationship(relationship);

                break;
        }
    }
}
