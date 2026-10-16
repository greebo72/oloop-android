# oloop-android

Android client for [oloop](https://github.com/greebo72/oloop-spec), a local cross-project tracker for todos, questions, decisions, features and bugs. It reads and writes the same Markdown files with YAML frontmatter as the [oloop CLI](https://github.com/greebo72/oloop-cli).

The code was generated with Google AI Studio from the oloop app brief. It is a first prototype and has not run on a device yet.

## What is in here

| Path | Content |
|---|---|
| `android/app/` | Kotlin and Jetpack Compose sources: file parser and writer, folder access through the Storage Access Framework, screens, unit tests |
| `src/` | A React web app that AI Studio used as a live preview, with its own TypeScript parser and test runner |

The Android app has no network permission. Task data stays in the folder you pick.

## Status

The Android sources are not a buildable Gradle project yet. `settings.gradle.kts`, the root `build.gradle.kts`, the version catalog (`gradle/libs.versions.toml`), `proguard-rules.pro` and the resources (`res/`, including the `Theme.Oloop` style and the launcher icons) are missing. The easiest start is a new project in Android Studio with the package `com.oloop.app`, then copy the Kotlin files into it.

Checked against the oloop spec, the parser and the storage code have a few mismatches that need fixing before the app works with real data:

- Tasks are read from and written to the project folder itself. The spec stores them in `<project>/tasks/`, so the app does not see tasks created by the CLI, and the CLI does not see tasks created by the app.
- `project.md` files with `referencePath` or `referenceHost` are rejected as having an unknown key. The parser expects an `updatedAt` key the spec does not define.
- A comment with an empty body is dropped while reading and is lost on the next save.
- A task file that cannot be parsed is overwritten on save instead of being left alone.

The unit tests use inline samples. They do not run the shared fixtures from oloop-spec yet.

## Run the web preview

You need Node.js. The web app does not call any API, so no key is needed.

```
npm install
npm run dev
```

The `@google/genai` and `express` dependencies and `.env.example` are leftovers from the AI Studio template.
