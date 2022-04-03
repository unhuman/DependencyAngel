package com.unhuman.dependencyresolver.tgf;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionTest {
    private static final Version VERSION_ONE = new Version("1");
    private static final Version VERSION_ONE_EXTRA = new Version("1.extra");
    private static final Version VERSION_TWO =  new Version("2");

    private static final Version SEMANTIC_VERSION_ONE = new Version("0.0.1");
    private static final Version SEMANTIC_VERSION_TWO = new Version("0.0.2");
    private static final Version SEMANTIC_VERSION_THREE = new Version("0.1.0");
    private static final Version SEMANTIC_VERSION_THREE_HOTFIX_TEXT = new Version("0.1.0.0text");
    private static final Version SEMANTIC_VERSION_THREE_HOTFIX = new Version("0.1.0.1");
    private static final Version SEMANTIC_VERSION_THREE_HOTFIX_TWO = new Version("0.1.0.2");

    private static final Version VERSION_ONE_SUFFIX_1 = new Version("1-1");
    private static final Version VERSION_ONE_SUFFIX_2 = new Version("1-2");
    private static final Version VERSION_ONE_SUFFIX_TEXT = new Version("1-SNAPSHOT");

    @Test
    public void validateSemantic() {
        Assertions.assertFalse(VERSION_ONE.isSemVer());
        Assertions.assertFalse(VERSION_ONE_EXTRA.isSemVer());
        Assertions.assertFalse(VERSION_TWO.isSemVer());
        Assertions.assertTrue(SEMANTIC_VERSION_ONE.isSemVer());
        Assertions.assertTrue(SEMANTIC_VERSION_TWO.isSemVer());
        Assertions.assertTrue(SEMANTIC_VERSION_THREE.isSemVer());
        Assertions.assertTrue(SEMANTIC_VERSION_THREE_HOTFIX_TEXT.isSemVer());
        Assertions.assertTrue(SEMANTIC_VERSION_THREE_HOTFIX.isSemVer());
        Assertions.assertTrue(SEMANTIC_VERSION_THREE_HOTFIX_TWO.isSemVer());
    }

    @Test
    public void validateGetVersion() {
        Assertions.assertEquals("0.1.0.2", SEMANTIC_VERSION_THREE_HOTFIX_TWO.getVersion());
    }

    @Test
    public void validateSuffix() {
        Assertions.assertEquals(1, VERSION_ONE.compareTo(VERSION_ONE_SUFFIX_1));
        Assertions.assertEquals(1, VERSION_ONE.compareTo(VERSION_ONE_SUFFIX_2));
        Assertions.assertEquals(1, VERSION_ONE.compareTo(VERSION_ONE_SUFFIX_TEXT));

        Assertions.assertEquals(-1, VERSION_ONE_SUFFIX_1.compareTo(VERSION_ONE_SUFFIX_2));
        Assertions.assertEquals(-1, VERSION_ONE_SUFFIX_1.compareTo(VERSION_ONE_SUFFIX_TEXT));
    }

    @Test
    public void test1() {
        Assertions.assertEquals(0, VERSION_ONE.compareTo(VERSION_ONE));
    }

    @Test
    public void test2() {
        Assertions.assertEquals(-1, VERSION_ONE.compareTo(VERSION_TWO));
    }

    @Test
    public void test3() {
        Assertions.assertEquals(1, VERSION_TWO.compareTo(VERSION_ONE));
    }

    @Test
    public void test4() {
        Assertions.assertEquals(-1, VERSION_ONE.compareTo(VERSION_ONE_EXTRA));
    }

    @Test
    public void test5() {
        Assertions.assertEquals(1, SEMANTIC_VERSION_ONE.compareTo(VERSION_ONE_EXTRA));
    }

    @Test
    public void test6() {
        Assertions.assertEquals(0, SEMANTIC_VERSION_ONE.compareTo(SEMANTIC_VERSION_ONE));
    }

    @Test
    public void test7() {
        Assertions.assertEquals(-1, SEMANTIC_VERSION_ONE.compareTo(SEMANTIC_VERSION_TWO));
    }

    @Test
    public void test8() {
        Assertions.assertEquals(-1, SEMANTIC_VERSION_ONE.compareTo(SEMANTIC_VERSION_THREE));
    }

    @Test
    public void testSemanticHotfix() {
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE.compareTo(SEMANTIC_VERSION_THREE_HOTFIX));
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TWO));
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TEXT));

        Assertions.assertEquals(1, SEMANTIC_VERSION_THREE_HOTFIX.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TEXT));
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE_HOTFIX.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TWO));
    }
}
