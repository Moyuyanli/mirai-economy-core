package xyz.cssxsh.mirai.economy.console.plus

import net.mamoe.mirai.contact.*
import xyz.cssxsh.mirai.economy.console.entity.*
import xyz.cssxsh.mirai.economy.service.*

@PublishedApi
internal class PlusUserEconomyAccount(
    private val record: EconomyAccountRecord,
    override val user: User
) : UserEconomyAccount, AbstractEconomyAccount() {
    override val uuid: String get() = record.uuid
    override val description: String get() = record.description
}

