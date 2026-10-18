package io.github.greebo72.oloop.core

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** Pure functions for creating projects. Same rules as the Python CLI. */
object ProjectOps {

    /**
     * The folder name of a project: NFKD-normalized, casefolded, ASCII only, runs of other
     * characters become "-", leading and trailing "-" are dropped. Same rules as `slugify()`
     * in the CLI. Java has no full casefold, so the one mapping that produces ASCII, ß to ss,
     * is applied explicitly; all other differences are non-ASCII and dropped anyway.
     * @throws FormatException if nothing is left as a slug.
     */
    fun slugify(name: String): String {
        val folded = name.lowercase(Locale.ROOT).replace("ß", "ss")
        val ascii = Normalizer.normalize(folded, Normalizer.Form.NFKD).filter { it.code < 128 }
        val slug = ascii.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (slug.isEmpty()) throw FormatException("project name yields no valid slug: '$name'")
        return slug
    }

    /**
     * A new project for [name]. [Project.folder] is the slug, `referencePath` and
     * `referenceHost` stay null: a phone has no repository path to point at.
     */
    fun create(
        name: String,
        now: String = TaskOps.nowUtc(),
        id: String = UUID.randomUUID().toString(),
    ): Project {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw FormatException("project name must not be empty")
        if (trimmed.length > 200) throw FormatException("project name must be at most 200 characters")
        return Project(
            formatVersion = FORMAT_VERSION,
            id = id,
            name = trimmed,
            createdAt = now,
            folder = slugify(trimmed),
        )
    }
}
