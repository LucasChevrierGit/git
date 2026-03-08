### Build and symlink command 

bazel build //src/main/java/mini_git:mini_git && sudo ln -sf "$(pwd)/bazel-bin/src/main/java/mini_git/mini_git" /usr/local/bin/minigit
