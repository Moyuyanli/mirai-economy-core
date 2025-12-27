package xyz.cssxsh.mirai.economy.console

import kotlinx.coroutines.*
import net.mamoe.mirai.console.extension.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.event.*
import xyz.cssxsh.mirai.economy.*
import xyz.cssxsh.mirai.economy.console.config.PlusDataConfig
import javax.script.*

@PublishedApi
internal object MiraiEconomyCorePlugin : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.cssxsh.mirai.plugin.mirai-economy-core",
        name = "mirai-economy-core",
        version = "1.1.1"
    ) {
        author("cssxsh")

        dependsOn("xyz.cssxsh.mirai.plugin.mirai-script-plugin", true)
        dependsOn("xyz.cssxsh.mirai.plugin.mirai-hibernate-plugin", true)
    }
) {

    override fun PluginComponentStorage.onLoad() {
        if (dataFolder.walk().any { it.name.startsWith(".js") }) {
            System.setProperty("xyz.cssxsh.mirai.script.js", "true")
        }
        if (dataFolder.walk().any { it.name.startsWith(".py") }) {
            System.setProperty("xyz.cssxsh.mirai.script.python", "true")
        }
    }

    override fun onEnable() {
        PlusDataConfig.reload()
        val lua = ScriptEngineManager(jvmPluginClasspath.pluginClassLoader).getEngineByName("lua")
        if (lua == null) {
            jvmPluginClasspath.downloadAndAddToPath(
                jvmPluginClasspath.pluginIndependentLibrariesClassLoader,
                listOf("org.luaj:luaj-jse:3.0.1")
            )
        }

        MiraiEconomyListenerHost.registerTo(globalEventChannel())
        EconomyService.reload(folder = dataFolderPath)
        EconomyService.register(currency = MiraiCoin, override = true)
    }

    override fun onDisable() {
        EconomyService.close()
        MiraiEconomyListenerHost.cancel()
    }
}