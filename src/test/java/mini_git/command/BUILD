load("@rules_java//java:defs.bzl", "java_test")

java_test(
    name =
    "MergeCommandTest",
    srcs = ["MergeCommandTest.java"],
    test_class = "mini_git.command.MergeCommandTest",
    deps = [
        "//src/main/java/mini_git:mini_git_lib",
        "@maven//:junit_junit",
    ],
)
