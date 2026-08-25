package dev.lumenchess.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalAssetDiscoveryTest {
    private static final List<String> PIECES = List.of(
        "bb.png", "bk.png", "bn.png", "bp.png", "bq.png", "br.png",
        "wb.png", "wk.png", "wn.png", "wp.png", "wq.png", "wr.png"
    );

    @TempDir Path temporaryDirectory;

    @Test
    void discoversOnlyCompleteCollectionsAndOnlyExpectedFiles() throws Exception {
        Path pieces = Files.createDirectories(temporaryDirectory.resolve("pieces"));
        Path complete = Files.createDirectories(pieces.resolve("ejgfv"));
        for (String name : PIECES) Files.writeString(complete.resolve(name), "png");
        Files.writeString(complete.resolve("unexpected.bin"), "private");
        Files.writeString(complete.resolve("script.py"), "private");

        Path incomplete = Files.createDirectories(pieces.resolve("incomplete"));
        for (String name : PIECES.subList(0, PIECES.size() - 1)) {
            Files.writeString(incomplete.resolve(name), "png");
        }
        Path invalidName = Files.createDirectories(pieces.resolve("bad name"));
        for (String name : PIECES) Files.writeString(invalidName.resolve(name), "png");

        Path boards = Files.createDirectories(temporaryDirectory.resolve("boards"));
        Files.writeString(boards.resolve("blue.png"), "png");
        Files.writeString(boards.resolve("unused.png"), "private");
        Files.writeString(temporaryDirectory.resolve("private-pack.zip"), "private");

        Object inventory = discover(temporaryDirectory);
        List<?> styles = (List<?>) inventory.getClass().getMethod("styles").invoke(inventory);
        List<?> copies = (List<?>) inventory.getClass().getMethod("copies").invoke(inventory);

        assertEquals(1, styles.size());
        Object style = styles.get(0);
        assertEquals("ejgfv", style.getClass().getMethod("sourceDirectory").invoke(style));
        assertEquals("Neo", style.getClass().getMethod("displayName").invoke(style));
        assertEquals("private.chesscom.ejgfv", style.getClass().getMethod("stableId").invoke(style));
        assertEquals(13, copies.size(), "12 canonical pieces plus the one required board");
        assertTrue(copies.stream().allMatch(copy -> {
            try {
                String relative = (String) copy.getClass().getMethod("relativeDestination").invoke(copy);
                return relative.endsWith(".png") &&
                    !relative.contains("unexpected") &&
                    !relative.contains("unused") &&
                    !relative.contains("private-pack");
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }));
    }

    @Test
    void stableIdsDoNotDependOnEnumerationOrder() throws Exception {
        createCompleteStyle("wood");
        createCompleteStyle("3d_staunton");

        Object inventory = discover(temporaryDirectory);
        List<?> styles = (List<?>) inventory.getClass().getMethod("styles").invoke(inventory);
        List<String> ids = styles.stream().map(style -> {
            try {
                return (String) style.getClass().getMethod("stableId").invoke(style);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }).toList();

        assertTrue(ids.contains("private.chesscom.wood"));
        assertTrue(ids.contains("private.chesscom.3d_staunton"));
        assertFalse(ids.contains("private.chesscom.0"));
        assertFalse(ids.contains("private.chesscom.1"));
    }

    @Test
    void canonicalStylesUseTheProductApprovedCatalogOrder() throws Exception {
        createCompleteStyle("3d_plastic");
        createCompleteStyle("wood");
        createCompleteStyle("ejgfv");

        Object inventory = discover(temporaryDirectory);
        List<?> styles = (List<?>) inventory.getClass().getMethod("styles").invoke(inventory);
        List<String> directories = styles.stream().map(style -> {
            try {
                return (String) style.getClass().getMethod("sourceDirectory").invoke(style);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }).toList();

        assertEquals(List.of("ejgfv", "wood", "3d_plastic"), directories);
    }

    @Test
    void rejectsTraversalOutsideTheConfiguredRoot() throws Exception {
        Class<?> discovery = Class.forName("dev.lumenchess.build.PersonalAssetDiscovery");
        Method safeRelative = discovery.getMethod("safeRelative", Path.class, String.class);

        InvocationTargetException error = assertThrows(
            InvocationTargetException.class,
            () -> safeRelative.invoke(null, temporaryDirectory, "../outside.png")
        );

        assertTrue(error.getCause() instanceof IllegalArgumentException);
    }

    private void createCompleteStyle(String name) throws Exception {
        Path style = Files.createDirectories(temporaryDirectory.resolve("pieces").resolve(name));
        for (String piece : PIECES) Files.writeString(style.resolve(piece), "png");
        Files.createDirectories(temporaryDirectory.resolve("boards"));
    }

    private static Object discover(Path root) throws Exception {
        Class<?> discovery = Class.forName("dev.lumenchess.build.PersonalAssetDiscovery");
        return discovery.getMethod("discover", Path.class).invoke(null, root);
    }
}
