package btcrenaud.corpse.persistence

import btcrenaud.corpse.entity.CorpseAccess
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileCorpseRepositoryTest {

    private val warnings = mutableListOf<String>()

    private fun directory(): File = Files.createTempDirectory("corpse-test").toFile().also { it.deleteOnExit() }

    private fun repository(file: File) = FileCorpseRepository(file) { warnings += it }

    private fun record(id: UUID = UUID.randomUUID(), experience: Int = 12) = CorpseRecord(
        corpseId = id,
        ownerUuid = UUID.randomUUID(),
        ownerName = "Steve",
        worldUid = UUID.randomUUID(),
        x = 1.5, y = 64.0, z = -3.5, yaw = 90f, pitch = 0f,
        inventory = emptyList(),
        experience = experience,
        createdAt = 1_000L,
        expiresAt = 0L,
        access = CorpseAccess(onlyOwnerCanLoot = true, ownerProtectionSeconds = 30),
        definitionId = "def",
    )

    @Test
    fun `a saved corpse is read back after a restart`() {
        val file = File(directory(), "corpses.json")
        val saved = record()
        repository(file).apply { initialize(); save(saved) }

        val reloaded = repository(file).apply { initialize() }.loadAll()
        assertEquals(1, reloaded.size)
        assertEquals(saved.corpseId, reloaded[0].corpseId)
        assertEquals(saved.access, reloaded[0].access)
        assertEquals(12, reloaded[0].experience)
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `a deleted corpse stays deleted after a restart`() {
        val file = File(directory(), "corpses.json")
        val saved = record()
        repository(file).apply { initialize(); save(saved); delete(saved.corpseId) }
        assertTrue(repository(file).apply { initialize() }.loadAll().isEmpty())
    }

    @Test
    fun `saving the same corpse again replaces it`() {
        val file = File(directory(), "corpses.json")
        val id = UUID.randomUUID()
        repository(file).apply { initialize(); save(record(id, 1)); save(record(id, 2)) }
        val reloaded = repository(file).apply { initialize() }.loadAll()
        assertEquals(listOf(2), reloaded.map { it.experience })
    }

    @Test
    fun `an unreadable file is kept aside and never overwritten silently`() {
        val folder = directory()
        val file = File(folder, "corpses.json")
        file.writeText("{ this is not json")

        val repository = repository(file)
        assertTrue(repository.initialize())
        assertTrue(repository.loadAll().isEmpty())
        assertTrue(warnings.any { "could not be read" in it }, warnings.toString())

        val backups = folder.listFiles { f -> f.name.startsWith("corpses.json.unreadable-") }.orEmpty()
        assertEquals(1, backups.size)
        assertEquals("{ this is not json", backups[0].readText())
    }

    @Test
    fun `a record with a corrupt id is skipped without losing the others`() {
        val file = File(directory(), "corpses.json")
        val good = record()
        repository(file).apply { initialize(); save(good) }
        file.writeText(file.readText().replace("[", """[{"corpseId":"nope","ownerUuid":"x","ownerName":"a","worldUid":"w","x":0.0,"y":0.0,"z":0.0,"yaw":0.0,"pitch":0.0,"inventory":"","experience":0,"createdAt":0,"expiresAt":0,"onlyOwnerCanLoot":false,"ownerProtectionSeconds":0,"definitionId":"d"},""" ))

        val reloaded = repository(file).apply { initialize() }.loadAll()
        assertEquals(listOf(good.corpseId), reloaded.map { it.corpseId })
        assertTrue(warnings.any { "invalid id" in it }, warnings.toString())
    }

    @Test
    fun `no temporary file is left behind after a write`() {
        val folder = directory()
        val file = File(folder, "corpses.json")
        repository(file).apply { initialize(); save(record()) }
        assertFalse(File(folder, "corpses.json.tmp").exists())
        assertTrue(file.exists())
    }
}