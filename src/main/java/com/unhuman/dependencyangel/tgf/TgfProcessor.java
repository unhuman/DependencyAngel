package com.unhuman.dependencyangel.tgf;

import com.unhuman.dependencyangel.dependency.Dependency;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class TgfProcessor {
    private enum Mode { DATA, RELATIONSHIPS }
    private Mode mode;
    private TgfData tgfData;

    protected TgfProcessor() {
        tgfData = new TgfData();
    }

    public static TgfData process(String filename) throws FileNotFoundException {
        return process(new Scanner(new File(filename)));
    }

    public static TgfData process(Scanner scanner) {
        TgfProcessor processor = new TgfProcessor();
        processor.readFile(scanner);
        scanner.close();
        return processor.tgfData;
    }


    protected void readFile(Scanner scanner) {
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

        if (!Mode.RELATIONSHIPS.equals(mode)) {
            throw new RuntimeException("Did not find relationships in dependency data.");
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
