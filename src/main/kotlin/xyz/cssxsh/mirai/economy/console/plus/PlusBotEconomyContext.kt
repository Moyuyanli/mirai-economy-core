package xyz.cssxsh.mirai.economy.console.plus

import net.mamoe.mirai.*
import net.mamoe.mirai.utils.*
import org.hibernate.*
import xyz.cssxsh.mirai.economy.service.*

@PublishedApi
internal class PlusBotEconomyContext(
    override val session: Session,
    override val service: PlusEconomyService,
    override val bot: Bot
) : BotEconomyContext, PlusSessionAction() {
    override val id: String = "bot[${bot.id}]-economy"

    override val logger: MiraiLogger = MiraiLogger.Factory.create(this::class, id)

    override var hard: EconomyCurrency by service.hard

    override val context: String get() = id
}

