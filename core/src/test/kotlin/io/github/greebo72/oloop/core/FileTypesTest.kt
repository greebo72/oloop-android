package io.github.greebo72.oloop.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FileTypesTest {
    @Test
    fun backupCopiesAreBinaryAndEverythingElseIsMarkdown() {
        assertEquals(FileTypes.BINARY, FileTypes.mimeTypeFor("33333333-3333-4333-8333-333333333333.md.bak"))
        assertEquals(FileTypes.BINARY, FileTypes.mimeTypeFor("x.md.BAK"))
        assertEquals(FileTypes.MARKDOWN, FileTypes.mimeTypeFor("33333333-3333-4333-8333-333333333333.md"))
        assertEquals(FileTypes.MARKDOWN, FileTypes.mimeTypeFor("project.md"))
    }
}
