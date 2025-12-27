package xyz.cssxsh.mirai.economy.console.plus

import cn.chahuyun.hibernateplus.HibernatePlusService
import cn.chahuyun.hibernateplus.DriveType
import xyz.cssxsh.mirai.economy.console.MiraiEconomyCorePlugin
import java.nio.file.Path
import kotlin.io.path.absolutePathString

public object PlusDatabaseLoader {

    public fun init(folder: Path) {
        val configuration = HibernatePlusService.createConfiguration(MiraiEconomyCorePlugin::class.java)
        configuration.classLoader = MiraiEconomyCorePlugin::class.java.classLoader
        configuration.packageName = "xyz.cssxsh.mirai.economy.console.entity"

        // 默认配置为 HSQLDB
        val dbPath = folder.resolve("economy.hsqldb.mv.db").absolutePathString()
        configuration.driveType = DriveType.HSQLDB
        configuration.address = dbPath

        HibernatePlusService.loadingService(configuration)
    }
}
