package com.unhuman.dependencyresolver.tgf;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Scanner;

public class TgfProcessorTest {
    private static final String DEPENDENCY_1 = "A com.unhuman:Sample1:jar:1.0.0-SNAPSHOT";
    private static final String DEPENDENCY_2 = "B com.unhuman:Sample2:jar:1.0.0-SNAPSHOT";
    private static final String SEPARATOR = "#";
    private static final String RELATIONSHIP_1 = "A B compile";

    public static final String VALID_TGF_DATA =
            DEPENDENCY_1 + '\n' + DEPENDENCY_2 + '\n' + SEPARATOR + '\n' + RELATIONSHIP_1;

    private static final String INVALID_TGF_DATA =
            DEPENDENCY_1;

    @Test
    public void testTgfData() {
        Scanner scanner = new Scanner(VALID_TGF_DATA);
        TgfData tgfData = TgfProcessor.process(scanner);
        Assertions.assertEquals("A", tgfData.getRootNode());
        Assertions.assertEquals(2, tgfData.getDependencies().size());
        Assertions.assertEquals(1, tgfData.getRelationships().size());
        Assertions.assertEquals("A", tgfData.getRelationships().get(0).getParent());
        Assertions.assertEquals("B", tgfData.getRelationships().get(0).getChild());
    }

    @Test
    public void testInvalidTgfData() {
        Scanner scanner = new Scanner(INVALID_TGF_DATA);

        Assertions.assertThrows(RuntimeException.class, () -> {
            TgfData tgfData = TgfProcessor.process(scanner);
        });
    }
}