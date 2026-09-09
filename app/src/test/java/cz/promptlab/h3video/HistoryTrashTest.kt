package cz.promptlab.h3video

import cz.promptlab.h3video.data.HistoryTrash
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryTrashTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun batchCanBeMovedAndRestoredWithoutChangingContents() {
        val sources = (1..3).map { folder.newFile("image$it.png").apply { writeText("content$it") } }
        val targets = sources.map { File(folder.root, "trash_${it.name}") }
        sources.zip(targets).forEach { (source, target) ->
            val content = source.readText()
            assertTrue(HistoryTrash.move(source, target))
            assertFalse(source.exists())
            assertEquals(content, target.readText())
        }
        targets.zip(sources).forEach { (source, target) -> assertTrue(HistoryTrash.move(source, target)) }
        sources.forEachIndexed { index, file -> assertEquals("content${index + 1}", file.readText()) }
    }

    @Test fun failedMoveDoesNotDestroyEitherFile() {
        val source = folder.newFile("source").apply { writeText("keep source") }
        val target = folder.newFile("target").apply { writeText("keep target") }
        assertFalse(HistoryTrash.move(source, target))
        assertEquals("keep source", source.readText())
        assertEquals("keep target", target.readText())
        assertFalse(HistoryTrash.move(source, File(folder.root, "missing/target")))
        assertTrue(source.isFile)
        assertFalse(HistoryTrash.move(File(folder.root, "missing"), File(folder.root, "new")))
    }
}
