package com.unhuman.dependencyangel.versioning;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class VersionTest {
    private static final String GROUP = "group";
    private static final String GROUP_NON_SEMANTIC = "group-ns";
    private static final String ARTIFACT = "artifact";

    private static final VersionHelper nonSemanticVersionHelper =
            new VersionHelper(Set.of(GROUP_NON_SEMANTIC + ":" + ARTIFACT));
    static {
        Version.setVersionHelper(nonSemanticVersionHelper);
    }

    private static final Version VERSION_ONE = new Version(GROUP, ARTIFACT, "1");
    private static final Version VERSION_ONE_EXTRA = new Version(GROUP, ARTIFACT, "1.extra");
    private static final Version VERSION_TWO =  new Version(GROUP, ARTIFACT, "2");

    private static final Version SEMANTIC_VERSION_ONE = new Version(GROUP, ARTIFACT, "0.0.1");
    private static final Version SEMANTIC_VERSION_TWO = new Version(GROUP, ARTIFACT, "0.0.2");
    private static final Version SEMANTIC_VERSION_THREE = new Version(GROUP, ARTIFACT, "0.1.0");
    private static final Version SEMANTIC_VERSION_THREE_HOTFIX_TEXT = new Version(GROUP, ARTIFACT, "0.1.0.0text");
    private static final Version SEMANTIC_VERSION_THREE_HOTFIX = new Version(GROUP, ARTIFACT, "0.1.0.1");
    private static final Version SEMANTIC_VERSION_THREE_HOTFIX_TWO = new Version(GROUP, ARTIFACT, "0.1.0.2");

    private static final Version VERSION_ONE_SUFFIX_1 = new Version(GROUP, ARTIFACT, "1-1");
    private static final Version VERSION_ONE_SUFFIX_2 = new Version(GROUP, ARTIFACT, "1-2");
    private static final Version VERSION_ONE_SUFFIX_TEXT = new Version(GROUP, ARTIFACT, "1-SNAPSHOT");

    private static final Version VERSION_ONE_NS = new Version(GROUP_NON_SEMANTIC, ARTIFACT, "1");
    private static final Version VERSION_ONE_EXTRA_NS = new Version(GROUP_NON_SEMANTIC, ARTIFACT, "1.extra");
    private static final Version VERSION_TWO_NS =  new Version(GROUP_NON_SEMANTIC, ARTIFACT, "2");
    private static final Version SEMANTIC_VERSION_ONE_NS = new Version(GROUP_NON_SEMANTIC, ARTIFACT, "0.0.1");


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

        Assertions.assertFalse(VERSION_ONE_NS.isSemVer());
        Assertions.assertFalse(VERSION_ONE_EXTRA_NS.isSemVer());
        Assertions.assertFalse(VERSION_TWO_NS.isSemVer());
        Assertions.assertFalse(SEMANTIC_VERSION_ONE_NS.isSemVer());
    }

    @Test
    public void validateGetVersion() {
        Assertions.assertEquals("0.1.0.2", SEMANTIC_VERSION_THREE_HOTFIX_TWO.toString());
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

    @Test
    public void testForceNonSemantic() {
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE.compareTo(SEMANTIC_VERSION_THREE_HOTFIX));
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TWO));
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TEXT));

        Assertions.assertEquals(1, SEMANTIC_VERSION_THREE_HOTFIX.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TEXT));
        Assertions.assertEquals(-1, SEMANTIC_VERSION_THREE_HOTFIX.compareTo(SEMANTIC_VERSION_THREE_HOTFIX_TWO));
    }

    @Test
    public void testNonSemantics() {
        Assertions.assertEquals(-1, VERSION_ONE_NS.compareTo(VERSION_ONE_EXTRA_NS));
        Assertions.assertEquals(-1, VERSION_ONE_NS.compareTo(VERSION_TWO));
        Assertions.assertEquals(1, VERSION_ONE_NS.compareTo(SEMANTIC_VERSION_ONE_NS));
    }

    @Test
    public void testNonSemanticVsSemantic() {
        // This is a weird test.  Should never occur (b/c we should never compare differing groupId:artifactId
        Assertions.assertEquals(0, VERSION_ONE_NS.compareTo(VERSION_ONE));
    }
}
