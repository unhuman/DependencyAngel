package com.unhuman.dependencyresolver.tgf;

import org.junit.jupiter.api.Test;

import java.util.Scanner;

public class TgfProcessorTest {
    private static String DEPENDENCY_1 = "662000775 com.unhuman:CouchbaseUI:jar:1.0.0-SNAPSHOT";
    private static String SEPARATOR = "#";
    private static String MAP_1 = "A B compile";

    @Test
    public void testTgfData() {
        String data = DEPENDENCY_1 + '\n' + SEPARATOR + '\n' + MAP_1;
        Scanner scanner = new Scanner(data);
        TgfProcessor tgfProcessor = new TgfProcessor(scanner);
    }
}
