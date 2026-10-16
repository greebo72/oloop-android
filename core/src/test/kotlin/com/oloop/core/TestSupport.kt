package com.oloop.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object TestSupport {
    /** Root of the oloop-spec submodule. Set by build.gradle.kts. */
    val spec: File = File(
        System.getProperty("oloop.spec") ?: error("system property oloop.spec is not set"),
    ).also { check(File(it, "fixtures").isDirectory) { "spec/ is empty, run: git submodule update --init" } }

    fun fixtureCases(kind: String): List<File> =
        File(spec, "fixtures/$kind").listFiles { f -> f.isDirectory }!!.sortedBy { it.name }

    /** A writable copy of a fixture data root. */
    fun copyRoot(case: File): Path {
        val target = Files.createTempDirectory("oloop-test")
        val source = File(case, "root").toPath()
        Files.walk(source).use { s ->
            s.forEach { p ->
                val dest = target.resolve(source.relativize(p).toString())
                if (Files.isDirectory(p)) Files.createDirectories(dest) else Files.copy(p, dest)
            }
        }
        return target
    }
}
