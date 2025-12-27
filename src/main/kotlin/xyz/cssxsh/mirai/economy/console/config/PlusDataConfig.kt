package xyz.cssxsh.mirai.economy.console.config

import cn.chahuyun.hibernateplus.DriveType
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

internal object PlusDataConfig : AutoSavePluginConfig("plus-data-config") {
    internal val dataType: DriveType by value(DriveType.HSQLDB)
    internal val dataUrl: String by value("localhost:3306/test")
    internal val dataUser: String by value("root")
    internal val dataPwd: String by value("123456")
}