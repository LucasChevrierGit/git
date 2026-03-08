package mini_git;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;



@CommandLine.Command(name = "add", description = "Add files to the staging area")
public class AddCommand implements Runnable {

    @CommandLine.Parameters(description = "Files paths to add")
    private String[] paths;

    private final Path current_dir = Path.of(".");
    private final Path indexPath = Path.of(".minigit", "index");


    @Override
    public void run() {
        List<String> filesToAdd = findUnstagedFile(paths);
        for (String file : filesToAdd) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] content = Files.readAllBytes(Path.of(file));
                byte[] hash = digest.digest(content);
                StringBuilder hashBuilder = new StringBuilder();
                for (byte b : hash) {
                    hashBuilder.append(String.format("%02x", b));
                }
                String hashS = hashBuilder.toString();

                Path objects = Path.of(".minigit","objects", hashS);
                if (Files.exists(objects)){
                    continue;
                }

                Files.write(objects, content);
                Files.write(indexPath,
                        (hashS + " " + file + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND,
                        StandardOpenOption.CREATE);

                System.out.println("Added file: " + file);
            } catch (Exception e) {
                System.err.println("Failed to read file: " + e.getMessage());
            }
        }
    }

    private List<String> findUnstagedFile(String[] args) {
        boolean addAll = Arrays.asList(args).contains(".");
        Set<String> indexed = new HashSet<>();
        if (Files.exists(indexPath)) {
            try {
                List<String> lines = Files.readAllLines(indexPath);
                for (String line : lines) {
                    String[] parts = line.split(" ", 2);
                    if (parts.length == 2) {
                        indexed.add(parts[1]);
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to read index: " + e.getMessage());
            }
        }

        Set<String> ignored = loadIgnore();
        try (Stream<Path> paths = Files.walk(this.current_dir)) {
            List<String> untracked = paths
                    .filter(Files::isRegularFile)
                    .map(p -> current_dir.relativize(p).toString())
                    .filter(p -> !p.startsWith(".minigit"))
                    .filter(p -> !isIgnored(p, ignored))
                    .filter(p -> addAll || Arrays.asList(args).contains(p))
                    .filter(p -> !indexed.contains(p))
                    .collect(Collectors.toList());

            if (!untracked.isEmpty()) {
                return untracked;
            }
        } catch (IOException e) {
            System.err.println("Failed to list files: " + e.getMessage());
        }

        return List.of();
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
