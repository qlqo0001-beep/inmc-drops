package com.inmc.drops

import com.inmc.drops.config.DropsConfig
import com.inmc.drops.config.Messages
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 배포 파일. */
class ResourceTest {

    private fun yaml(path: String): YamlConfiguration =
        javaClass.classLoader.getResourceAsStream(path)!!.use { YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8)) }

    private val code: String by lazy {
        File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    }

    @Test
    fun `배포 메시지와 기본값 표의 키가 정확히 같다`() {
        assertEquals(Messages.DEFAULTS.keys, yaml("messages.yml").getKeys(false))
    }

    @Test
    fun `코드가 부르는 메시지 키가 전부 있다`() {
        // 없는 키는 오류 없이 빈 줄이 된다 — 무엇이 실패했는지 아무도 모른다.
        val used = Regex("""send\([^,()]+, (?:if \([^)]*\) )?"([a-z-]+)"(?: else "([a-z-]+)")?""").findAll(code)
            .flatMap { listOf(it.groupValues[1], it.groupValues[2]) }.filter { it.isNotEmpty() }.toSet()
        val components = Regex("""component\("([a-z-]+)"""").findAll(code).map { it.groupValues[1] }.toSet()
        assertTrue(used.size > 15, "키를 못 읽었다: $used")
        assertEquals(emptySet(), (used + components) - Messages.DEFAULTS.keys)
    }

    @Test
    fun `배포 설정은 코드의 기본값과 같다`() {
        assertEquals(DropsConfig(), DropsConfig.from(yaml("config.yml")))
    }

    @Test
    fun `코드가 쓰는 권한은 전부 선언돼 있다`() {
        // 선언하지 않은 권한은 Bukkit 이 op 기본으로 다룬다(지뢰 11) — info 가 일반 플레이어에게 안 열린다.
        val yml = File("src/main/resources/paper-plugin.yml").readText()
        for (node in listOf(Drops.ADMIN, Drops.INFO)) assertTrue(Regex("""(?m)^  ${Regex.escape(node)}:""").containsMatchIn(yml), node)
        assertTrue(Regex("""(?ms)^  inmcdrops\.info:.*?default: true""").containsMatchIn(yml), "정보는 누구나")
    }
}
