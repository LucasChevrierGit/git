package mini_git;

import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "status", description = "Show the working tree status")
public class StatusCommand implements Runnable {


    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private static Path current_directory = Path.of(".");

    @Override
    public void run() {
        Path root = Path.of(".minigit");

        if (!Files.exists(root)) {
            System.err.println("Not a mini_git repository (no .minigit directory).");
            return;
        }

        Path indexPath = root.resolve("index");
        Map<String, String> indexed = loadIndex(indexPath);
        Set<String> working = listWorkingFiles();

        // Section 1: Changes to be committed (staged files)
        // new file = in index, exists on disk
        // deleted  = in index, no longer on disk
        // modified = in index, on disk but hash changed
        List<String> stagedNew = new ArrayList<>();
        List<String> stagedDeleted = new ArrayList<>();
        List<String> stagedModified = new ArrayList<>();
        for (Map.Entry<String, String> entry : indexed.entrySet()) {
            String file = entry.getKey();
            String storedHash = entry.getValue();
            if (!working.contains(file)) {
                stagedDeleted.add(file);
            } else {
                String currentHash = computeHash(Path.of(file));
                if (currentHash != null && !currentHash.equals(storedHash)) {
                    stagedModified.add(file);
                } else {
                    stagedNew.add(file);
                }
            }
        }
        Collections.sort(stagedNew);
        Collections.sort(stagedDeleted);
        Collections.sort(stagedModified);
        if (!stagedNew.isEmpty() || !stagedDeleted.isEmpty() || !stagedModified.isEmpty()) {
            System.out.println("Changes to be committed:");
            System.out.println("  (use \"minigit restore --staged <file>...\" to unstage)");
            for (String file : stagedNew) {
                System.out.println(GREEN + "        new file:   " + file + RESET);
            }
            for (String file : stagedModified) {
                System.out.println(GREEN + "        modified:   " + file + RESET);
            }
            for (String file : stagedDeleted) {
                System.out.println(GREEN + "        deleted:    " + file + RESET);
            }
            System.out.println();
        }

        // Section 2: Changes not staged for commit
        // These are files that were staged, modified since, but not re-added
        if (!stagedModified.isEmpty() || !stagedDeleted.isEmpty()) {
            System.out.println("Changes not staged for commit:");
            System.out.println("  (use \"minigit add <file>...\" to update what will be committed)");
            for (String file : stagedModified) {
                System.out.println(RED + "        modified:   " + file + RESET);
            }
            for (String file : stagedDeleted) {
                System.out.println(RED + "        deleted:    " + file + RESET);
            }
            System.out.println();
        }

        // Section 3: Untracked files
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

        if (stagedNew.isEmpty() && stagedModified.isEmpty() && stagedDeleted.isEmpty() && untracked.isEmpty()) {
            System.out.println("Nothing to report, working tree clean.");
        }
    }

    private Map<String, String> loadIndex(Path indexPath) {
        if (!Files.exists(indexPath)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, String> index = new LinkedHashMap<>();
            for (String line : Files.readAllLines(indexPath)) {
                String[] parts = line.split(" ", 2);
                if (parts.length == 2) {
                    index.put(parts[1], parts[0]);
                }
            }
            return index;
        } catch (IOException e) {
            System.err.println("Failed to read index: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String computeHash(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(Files.readAllBytes(filePath));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> listWorkingFiles() {
        Set<String> ignored = loadIgnore();
        try (Stream<Path> paths = Files.walk(current_directory)) {
            return paths
                .filter(Files::isRegularFile)
                .map(p -> current_directory.relativize(p).toString())
                .filter(p -> !p.startsWith(".minigit"))
                .filter(p -> !isIgnored(p, ignored))
                .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println("Failed to list working files: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<String> loadIgnore() {
        Path ignorePath = Path.of(".minigit", "ignore");
        if (!Files.exists(ignorePath)) {
            return Collections.emptySet();
        }
        try {
            return Files.readAllLines(ignorePath).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    private boolean isIgnored(String filePath, Set<String> patterns) {
        for (String pattern : patterns) {
            String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
            if (filePath.matches(regex) || filePath.matches(".*/" + regex)) {
                return true;
            }
        }
        return false;
    }
}
