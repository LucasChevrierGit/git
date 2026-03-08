package mini_git;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "init", description = "Initialize a new mini_git repository")
public class InitCommand implements Runnable {

    @Override
    public void run() {
        Path root = Path.of(".minigit");

        if (Files.exists(root)) {
            System.out.println("Already a mini_git repository.");
            return;
        }

        try {
            Files.createDirectories(root.resolve("objects"));
            Files.createDirectories(root.resolve("refs"));
            Files.writeString(root.resolve("HEAD"), "ref: refs/main\n");
            Files.createFile(root.resolve("index"));

            // Basic setup
            Files.writeString(root.resolve("ignore"), ".*\nbazel*\n");

            Config config = new Config();
            config.set("core", "repositoryformatversion", "0");
            config.save(root.resolve("config"));

            System.out.println("Initialized empty mini_git repository in " + root.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to initialize repository: " + e.getMessage());
        }
    }


}
