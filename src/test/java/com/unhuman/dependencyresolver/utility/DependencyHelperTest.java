package com.unhuman.dependencyresolver.utility;

import com.unhuman.dependencyresolver.tgf.TgfData;
import com.unhuman.dependencyresolver.tgf.TgfProcessor;
import com.unhuman.dependencyresolver.tree.DependencyNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Scanner;

import static com.unhuman.dependencyresolver.tgf.TgfProcessorTest.VALID_TGF_DATA;

public class DependencyHelperTest {
    @Test
    public void DependencyHelperTest() {
        Scanner scanner = new Scanner(VALID_TGF_DATA);
        TgfData tgfData = TgfProcessor.process(scanner);

        DependencyNode root = DependencyHelper.convertTgfData(tgfData);
        Assertions.assertNotNull(root);
        Assertions.assertNull(root.getParent());
        Assertions.assertEquals(1, root.getChildren().size());
        Assertions.assertEquals(root, root.getChildren().get(0).getParent());
        Assertions.assertEquals(0, root.getChildren().get(0).getChildren().size());
    }
}
