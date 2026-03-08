package mini_git;

import mini_git.command.*;
import picocli.CommandLine;

@CommandLine.Command(
    name = "minigit",
    mixinStandardHelpOptions = true,
    version = "minigit 1.0",
    description = "A minimal git implementation",
    subcommands = {
        InitCommand.class,
        StatusCommand.class,
        AddCommand.class,
        RestoreCommand.class,
        RemoteCommand.class,
        PushCommand.class,
        CommitCommand.class,
        FetchCommand.class,
        MergeCommand.class,
    }
)
public class Main implements Runnable {

    @Override
    public void run() {
        System.out.println("Usage: minigit <command> [<args>]");
        System.out.println("Try 'minigit --help' for more information.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main())
                .setExecutionStrategy(new CommandLine.RunLast())
                .execute(args);
        System.exit(exitCode);
    }
}
