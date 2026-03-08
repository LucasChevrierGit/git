package mini_git.command;

import mini_git.util.Config;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(name = "remote", description = "Manage remote repositories")
public class RemoteCommand implements Runnable {

    @CommandLine.Parameters(index = "0", description = "Subcommand (e.g. add)")
    private String action;

    @CommandLine.Parameters(index = "1", description = "Remote name (e.g. origin)")
    private String name;

    @CommandLine.Parameters(index = "2", description = "Remote URL (e.g. https://github.com/owner/repo)")
    private String url;

    @CommandLine.Option(names = "--token", description = "GitHub personal access token", required = true)
    private String token;

    private final Path configPath = Path.of(".minigit", "config");

    @Override
    public void run() {
        if (!"add".equals(action)) {
            System.err.println("Unknown remote subcommand: " + action);
            return;
        }

        Config config = new Config();
        config.load(configPath);
        config.set("remote", "url", url);
        config.set("remote", "token", token);
        config.save(configPath);

        System.out.println("Remote '" + name + "' added: " + url);
    }
}
