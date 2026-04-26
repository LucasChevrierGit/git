load("@rules_java//java:defs.bzl", "java_binary", "java_library")

java_library(
    name = "mini_git_lib",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:info_picocli_picocli",
        "@maven//:io_github_java_diff_utils_java_diff_utils",
    ],
)

java_binary(
    name = "mini_git",
    main_class = "mini_git.Main",
    runtime_deps = [":mini_git_lib"],
)
