package io.github.greebo72.oloop.core

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectTest {

    // ---- slugify: parity with the CLI ----

    @Test
    fun `slugify basic names`() {
        assertEquals("my-project", ProjectOps.slugify("My Project"))
        assertEquals("oloop-android", ProjectOps.slugify("oloop-android"))
        assertEquals("a1-b2", ProjectOps.slugify("a1 b2"))
    }

    @Test
    fun `slugify folds diacritics like NFKD in the CLI`() {
        assertEquals("uber", ProjectOps.slugify("Über"))
        assertEquals("creme-brulee", ProjectOps.slugify("Crème Brûlée"))
    }

    @Test
    fun `slugify maps sharp s to ss like python casefold`() {
        // Python casefold() turns ß into ss; Kotlin lowercase() alone would drop it.
        assertEquals("strasse", ProjectOps.slugify("Straße"))
    }

    @Test
    fun `slugify collapses punctuation and trims dashes`() {
        assertEquals("hello-world", ProjectOps.slugify("  Hello, World!!!  "))
        assertEquals("x", ProjectOps.slugify("--x--"))
    }

    @Test
    fun `slugify refuses names without ascii letters or digits`() {
        assertFailsWith<FormatException> { ProjectOps.slugify("") }
        assertFailsWith<FormatException> { ProjectOps.slugify("---") }
        assertFailsWith<FormatException> { ProjectOps.slugify("日本語") }
    }

    // ---- serializeProject ----

    @Test
    fun `serializeProject writes cli key order and quoted scalars`() {
        val project = ProjectOps.create("My Project", now = "2026-10-17T10:00:00Z", id = "id-1")
        val expected = """
            ---
            formatVersion: 1
            id: "id-1"
            name: "My Project"
            createdAt: "2026-10-17T10:00:00Z"
            ---
        """.trimIndent()
        assertEquals(expected, OloopWriter.serializeProject(project).trim())
    }

    @Test
    fun `serializeProject omits null reference fields`() {
        val project = ProjectOps.create("P", now = "2026-10-17T10:00:00Z", id = "id-2")
        val text = OloopWriter.serializeProject(project)
        assertTrue("referencePath" !in text)
        assertTrue("referenceHost" !in text)
        assertNull(project.referencePath)
        assertNull(project.referenceHost)
    }

    // ---- createProject via the repository ----

    private fun tempFs(): Pair<OloopRepository, java.nio.file.Path> {
        val dir = Files.createTempDirectory("oloop-test")
        return OloopRepository(JavaFileSystem(dir)) to dir
    }

    @Test
    fun `createProject writes project md and tasks folder`() {
        val (repo, dir) = tempFs()
        val result = repo.createProject("  My Project  ")
        val created = assertIs<ProjectCreateResult.Created>(result)
        assertEquals("My Project", created.project.name)
        assertEquals("my-project", created.project.folder)

        val projectFile = dir.resolve("my-project/project.md")
        assertTrue(Files.isRegularFile(projectFile))
        assertTrue(Files.isDirectory(dir.resolve("my-project/tasks")))

        // The CLI needs tasks/ to exist: its atomic write makes a temp file in that folder.
        val parsed = OloopParser.parseProject(Files.readString(projectFile), "my-project")
        assertEquals(created.project.copy(), parsed)
    }

    @Test
    fun `createProject output loads through loadAll`() {
        val (repo, _) = tempFs()
        val created = assertIs<ProjectCreateResult.Created>(repo.createProject("Load Check"))
        val load = repo.loadAll()
        assertTrue(load.unreadable.isEmpty())
        assertEquals(listOf(created.project.name), load.projects.map { it.project.name })
    }

    @Test
    fun `createProject refuses duplicate name case insensitively`() {
        val (repo, _) = tempFs()
        assertIs<ProjectCreateResult.Created>(repo.createProject("Demo"))
        val again = assertIs<ProjectCreateResult.Refused>(repo.createProject("dEMO"))
        assertTrue("already exists" in again.reason)
    }

    @Test
    fun `createProject refuses when slug folder exists`() {
        val (repo, dir) = tempFs()
        dir.resolve("taken").createDirectories()
        val result = assertIs<ProjectCreateResult.Refused>(repo.createProject("Taken"))
        assertTrue("folder taken already exists" in result.reason)
    }

    @Test
    fun `createProject refuses blank and invalid names`() {
        val (repo, _) = tempFs()
        assertIs<ProjectCreateResult.Refused>(repo.createProject("   "))
        assertIs<ProjectCreateResult.Refused>(repo.createProject("---"))
    }

    @Test
    fun `createProject refuses when an existing project md is unreadable`() {
        val (repo, dir) = tempFs()
        val broken = dir.resolve("broken")
        broken.resolve("tasks").createDirectories()
        Files.writeString(broken.resolve("project.md"), "not frontmatter at all")
        val result = assertIs<ProjectCreateResult.Refused>(repo.createProject("Something New"))
        assertTrue("cannot be read" in result.reason)
        // Nothing was written for the refused project.
        assertTrue(!Files.exists(dir.resolve("something-new")))
    }
}
