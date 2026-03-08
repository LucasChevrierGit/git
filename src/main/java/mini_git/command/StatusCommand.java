package mini_git.command;

import mini_git.core.IndexManager;
import mini_git.core.ObjectStore;
import mini_git.util.IgnoreManager;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "status", description = "Show the working tree status")
public class StatusCommand implements Runnable {

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private final static Path current_directory = Path.of(".");

    @Override
    public void run() {
        Path root = Path.of(".minigit");

        if (!Files.exists(root)) {
            System.err.println("Not a mini_git repository (no .minigit directory).");
            return;
        }

        Map<String, String> indexed = IndexManager.loadIndex();
        Set<String> working = listWorkingFiles();

        List<String> stagedNew = new ArrayList<>();
        List<String> unstagedDeleted = new ArrayList<>();
        List<String> unstagedModified = new ArrayList<>();
        for (Map.Entry<String, String> entry : indexed.entrySet()) {
            String file = entry.getKey();
            String storedHash = entry.getValue();
            if (!working.contains(file)) {
                unstagedDeleted.add(file);
            } else {
                String currentHash = ObjectStore.computeHash(Path.of(file));
                if (currentHash != null && !currentHash.equals(storedHash)) {
                    unstagedModified.add(file);
                }
                stagedNew.add(file);
            }
        }
        Collections.sort(stagedNew);
        Collections.sort(unstagedDeleted);
        Collections.sort(unstagedModified);
        if (!stagedNew.isEmpty()) {
            System.out.println("Changes to be committed:");
            System.out.println("  (use \"minigit restore --staged <file>...\" to unstage)");
            for (String file : stagedNew) {
                System.out.println(GREEN + "        new file:   " + file + RESET);
            }
            System.out.println();
        }

        if (!unstagedModified.isEmpty() || !unstagedDeleted.isEmpty()) {
            System.out.println("Changes not staged for commit:");
            System.out.println("  (use \"minigit add <file>...\" to update what will be committed)");
            for (String file : unstagedModified) {
                System.out.println(RED + "        modified:   " + file + RESET);
            }
            for (String file : unstagedDeleted) {
                System.out.println(RED + "        deleted:    " + file + RESET);
            }
            System.out.println();
        }

        Set<String> untracked = new TreeSet<>(working);
        untracked.removeAll(indexed.keySet());
        if (!untracked.isEmpty()) {
            System.out.println("Untracked files:");
            System.out.println("  (use \"minigit add <file>...\" to include in what will be committed)");
            for (String file : untracked) {
                System.out.println(RED + "        " + file + RESET);
            }
            System.out.println();
        }

        if (stagedNew.isEmpty() && unstagedModified.isEmpty() && unstagedDeleted.isEmpty() && untracked.isEmpty()) {
            System.out.println("Nothing to report, working tree clean.");
        }
    }

    private Set<String> listWorkingFiles() {
        Set<String> ignored = IgnoreManager.loadIgnore();
        try (Stream<Path> paths = Files.walk(current_directory)) {
            return paths
                .filter(Files::isRegularFile)
                .map(p -> current_directory.relativize(p).toString())
                .filter(p -> !p.startsWith(".minigit"))
                .filter(p -> !IgnoreManager.isIgnored(p, ignored))
                .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println("Failed to list working files: " + e.getMessage());
            return Collections.emptySet();
        }
    }
}
