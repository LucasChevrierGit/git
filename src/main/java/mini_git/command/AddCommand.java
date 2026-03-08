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

@CommandLine.Command(name = "add", description = "Add files to the staging area")
public class AddCommand implements Runnable {

    @CommandLine.Parameters(description = "Files paths to add")
    private String[] paths;

    private final Path current_dir = Path.of(".");

    @Override
    public void run() {
        Map<String, String> indexed = IndexManager.loadIndex();
        Set<String> ignored = IgnoreManager.loadIgnore();
        List<String> filesToAdd = findFilesToAdd(paths, indexed, ignored);

        for (String file : filesToAdd) {
            try {
                byte[] content = Files.readAllBytes(Path.of(file));
                String hashS = ObjectStore.storeBlob(content);

                if (hashS.equals(indexed.get(file))) {
                    continue;
                }

                indexed.put(file, hashS);
                System.out.println("Added file: " + file);
            } catch (Exception e) {
                System.err.println("Failed to read file: " + e.getMessage());
            }
        }

        IndexManager.writeIndex(indexed);
    }

    private List<String> findFilesToAdd(String[] args, Map<String, String> indexed, Set<String> ignored) {
        boolean addAll = Arrays.asList(args).contains(".");

        try (Stream<Path> paths = Files.walk(this.current_dir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(p -> current_dir.relativize(p).toString())
                    .filter(p -> !p.startsWith(".minigit"))
                    .filter(p -> !IgnoreManager.isIgnored(p, ignored))
                    .filter(p -> addAll || Arrays.asList(args).contains(p))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Failed to list files: " + e.getMessage());
        }

        return List.of();
    }
}
