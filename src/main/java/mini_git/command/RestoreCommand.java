package mini_git.command;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(name = "restore", description = "Restore working tree files")
public class RestoreCommand implements Runnable {

    @CommandLine.Option(names = "--staged", description = "Unstage files (remove from index)")
    private boolean staged;

    @CommandLine.Parameters(description = "File paths to restore")
    private String[] paths;

    private final Path indexPath = Path.of(".minigit", "index");

    @Override
    public void run() {
        if (!Files.exists(Path.of(".minigit"))) {
            System.err.println("Not a mini_git repository (no .minigit directory).");
            return;
        }

        if (!staged) {
            System.err.println("Only --staged is supported for now.");
            return;
        }

        if (!Files.exists(indexPath)) {
            System.err.println("Nothing to restore (index is empty).");
            return;
        }

        try {
            List<String> lines = Files.readAllLines(indexPath);
            List<String> remaining = lines.stream()
                .filter(line -> {
                    String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        String filePath = parts[1];
                        for (String path : paths) {
                            if (filePath.equals(path)) {
                                System.out.println("Unstaged: " + filePath);
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

            Files.writeString(indexPath, String.join(System.lineSeparator(), remaining)
                + (remaining.isEmpty() ? "" : System.lineSeparator()));

        } catch (IOException e) {
            System.err.println("Failed to update index: " + e.getMessage());
        }
    }
}
