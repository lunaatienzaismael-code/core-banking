package com.ledgerflow.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads files as they were at the last release tag ({@code v*}) through git, so the check needs no registry. */
final class LastRelease {

    private final Path moduleDirectory;
    private final String tag;

    private LastRelease(Path moduleDirectory, String tag) {
        this.moduleDirectory = moduleDirectory;
        this.tag = tag;
    }

    static LastRelease of(Path moduleDirectory) {
        var tag = git(moduleDirectory, "describe", "--tags", "--abbrev=0", "--match", "v*");
        if (tag.exitCode() != 0) {
            throw new IllegalStateException("No release tag found; a CI checkout needs its tags (fetch-depth: 0): "
                    + tag.output().strip());
        }
        return new LastRelease(moduleDirectory, tag.output().strip());
    }

    String tag() {
        return tag;
    }

    /** Files directly under the directory at the tag; empty if the directory did not exist then. */
    List<Path> list(Path directoryRelativeToModule) {
        var listed =
                git(moduleDirectory, "ls-tree", "--name-only", tag, "./" + slashed(directoryRelativeToModule) + "/");
        if (listed.exitCode() != 0) {
            return List.of();
        }
        // ls-tree prints paths relative to the directory git runs in, i.e. the module.
        return listed.output()
                .lines()
                .filter(line -> !line.isBlank())
                .map(Path::of)
                .toList();
    }

    /** The file's content at the tag, or empty if it did not exist then. */
    Optional<String> read(Path relativeToModule) {
        var shown = git(moduleDirectory, "show", tag + ":./" + slashed(relativeToModule));
        return shown.exitCode() == 0 ? Optional.of(shown.output()) : Optional.empty();
    }

    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record Result(int exitCode, String output) {}

    private static Result git(Path directory, String... arguments) {
        var command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new Result(process.waitFor(), output);
        } catch (IOException exception) {
            throw new IllegalStateException("git is needed to read the last release", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git", exception);
        }
    }
}
