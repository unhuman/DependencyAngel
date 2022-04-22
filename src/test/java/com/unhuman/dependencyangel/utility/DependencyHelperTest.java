package com.unhuman.dependencyangel.utility;

import com.unhuman.dependencyangel.tgf.TgfData;
import com.unhuman.dependencyangel.tgf.TgfProcessor;
import com.unhuman.dependencyangel.tree.DependencyNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Scanner;

import static com.unhuman.dependencyangel.tgf.TgfProcessorTest.VALID_TGF_DATA;

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
