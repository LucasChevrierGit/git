package mini_git;

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
        RestoreCommand.class
    }
)
public class Main implements Runnable {

    @Override
    public void run() {
        System.out.println("Usage: minigit <command> [<args>]");
        System.out.println("Try 'minigit --help' for more information.");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
