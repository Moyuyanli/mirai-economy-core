package xyz.cssxsh.mirai.economy.console.plus

import cn.chahuyun.hibernateplus.DriveType.*
import cn.chahuyun.hibernateplus.HibernatePlusService
import xyz.cssxsh.mirai.economy.console.MiraiEconomyCorePlugin
import xyz.cssxsh.mirai.economy.console.config.PlusDataConfig
import java.nio.file.Path

public object PlusDatabaseLoader {

    public fun init(folder: Path) {
        val configuration = HibernatePlusService.createConfiguration(MiraiEconomyCorePlugin::class.java)
        configuration.classLoader = MiraiEconomyCorePlugin::class.java.classLoader
        configuration.packageName = "xyz.cssxsh.mirai.economy.console.entity"

        val dataType = PlusDataConfig.dataType
        when (dataType) {
            // 默认配置为 HSQLDB
            HSQLDB -> configuration.address = folder.resolve("economy.hsqldb.mv.db").toString()
            MYSQL -> {
                configuration.address = PlusDataConfig.dataUrl
                configuration.user = PlusDataConfig.dataUser
                configuration.password = PlusDataConfig.dataPwd
            }

            H2 -> configuration.address = folder.resolve("economy.h2.mv.db").toString()
            SQLITE -> configuration.address = folder.resolve("economy.mv.db").toString()
            MARIADB -> {
                configuration.address = PlusDataConfig.dataUrl
                configuration.user = PlusDataConfig.dataUser
                configuration.password = PlusDataConfig.dataPwd
            }

            DUCKDB -> configuration.address = folder.resolve("economy.duckdb").toString()
        }
        configuration.driveType = dataType

        HibernatePlusService.loadingService(configuration)
    }
}
