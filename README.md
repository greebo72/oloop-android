# oloop-android

Android client for [oloop](https://github.com/greebo72/oloop-spec), a local cross-project tracker for todos, questions, decisions, features and bugs. It reads and writes the same Markdown files with YAML frontmatter as the [oloop CLI](https://github.com/greebo72/oloop-cli).

Status: the `core` module is done. The Android app module (screens and folder access) is the next step.

## Modules

| Module | Content |
|---|---|
| `core` | Plain Kotlin/JVM library: parser, writer, behavior rules, conflict check, backup. No Android dependency, so it runs and is tested on any JDK |
| `app` | Not started. Jetpack Compose screens and a Storage Access Framework implementation of `OloopFileSystem` |

`spec/` is the [oloop-spec](https://github.com/greebo72/oloop-spec) repo as a Git submodule. Its schemas and conformance fixtures are the contract. The fixtures run as unit tests.

## What `core` does

- Reads `project.md` and `<project>/tasks/<id>.md`, checks them against the spec rules and refuses files with a newer `formatVersion`. Files it cannot read are reported, not hidden, and never written.
- Writes task files in the same layout as the CLI. Timestamps are plain strings, so they never change.
- `TaskOps` holds the rules: create, edit, close, reopen, comment. Every change needs an actor name and sets `updatedAt` and `updatedBy`.
- `OloopRepository.saveTask` re-reads the file first. If `updatedAt` on disk differs from what the caller loaded, nothing is written and a conflict is returned. Before an existing file is replaced, it is copied to `<project>/.backup/<id>.md.bak`.
- `OloopFileSystem` is the only folder access `core` needs. `JavaFileSystem` implements it for tests and desktop use, the Android app will implement it with the Storage Access Framework.

## Build and test

You need JDK 21. The Gradle wrapper downloads Gradle itself.

```
git clone --recurse-submodules git@github.com:greebo72/oloop-android.git
cd oloop-android
./gradlew :core:test
```

To check a real data folder without writing to it:

```
OLOOP_READ_ROOT=/path/to/data ./gradlew :core:test --tests '*ManualChecks*'
```

## History

The first version was generated with Google AI Studio. It stored tasks in the wrong folder and could not read projects with reference fields, so it was replaced. It is still available at commit `5e9af62`.
