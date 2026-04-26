package mini_git.command;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class MergeCommandTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final MergeCommand merge = new MergeCommand();

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @Before
    public void setUp() throws IOException {
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        Path tempDir = tempFolder.getRoot().toPath();
        System.setProperty("user.dir", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".minigit/objects"));
        Files.createDirectories(tempDir.resolve(".minigit/refs/remotes/origin"));
        Files.writeString(tempDir.resolve(".minigit/HEAD"), "ref: refs/main\n");
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void writeConflictFile_noBase() throws IOException {
        // TODO
    }

    @Test
    public void writeConflictFile_withBase() throws IOException {
        Path file = Path.of("/Users/lucas.chevrier/perso/mini_git/src/test/resources/conflict_test.txt");
        byte[] base = "line1\nline2\nline3\nline 4\n".getBytes();
        byte[] ours = "line1\nchanged by us\nline3\nyeah boy\n".getBytes();
        byte[] theirs = "line1\nchanged by them\nline3\nline 4\n".getBytes();

        merge.writeConflictFile(file, base, ours, theirs, "origin/main");

        String printed = out.toString();



        String result = Files.readString(file);

        originalOut.println(result);
        assertTrue(result.contains("<<<<<<< HEAD"));
        assertTrue(result.contains("changed by us"));
        assertTrue(result.contains("======="));
        assertTrue(result.contains("changed by them"));
        assertTrue(result.contains(">>>>>>> origin/main"));
    }
}
