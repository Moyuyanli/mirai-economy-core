package xyz.cssxsh.mirai.economy.console.plus

import cn.chahuyun.hibernateplus.HibernateFactory
import cn.chahuyun.hibernateplus.HibernatePlusService
import kotlinx.coroutines.*
import net.mamoe.mirai.*
import net.mamoe.mirai.console.permission.*
import net.mamoe.mirai.console.plugin.jvm.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.utils.*
import org.hibernate.*
import xyz.cssxsh.mirai.economy.console.*
import xyz.cssxsh.mirai.economy.console.entity.*
import xyz.cssxsh.mirai.economy.script.*
import xyz.cssxsh.mirai.economy.service.*
import java.nio.file.*
import java.util.*
import kotlin.coroutines.*
import kotlin.io.path.*
import kotlin.reflect.*

@PublishedApi
@EconomyServiceInfo(name = "plus", ignore = [NoClassDefFoundError::class])
internal class PlusEconomyService : IEconomyService, AbstractEconomyService() {
    override val id: String = "economy.service.plus"

    private lateinit var factory: SessionFactory

    override val coroutineContext: CoroutineContext = kotlin.run {
        try {
            MiraiEconomyCorePlugin.coroutineContext
        } catch (_: ExceptionInInitializerError) {
            CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) {
                    logger.error({ "Exception in coroutine PlusEconomyService" }, throwable)
                }
            }
        } + CoroutineName(id)
    }

    private fun <T> fromSession(block: (Session) -> T): T {
        return factory.openSession().use(block)
    }

    private fun <T> fromTransaction(block: (Session) -> T): T {
        return factory.openSession().use { session ->
            val transaction = session.beginTransaction()
            try {
                val result = block(session)
                transaction.commit()
                result
            } catch (e: Throwable) {
                if (transaction.isActive) transaction.rollback()
                throw e
            }
        }
    }

    @Synchronized
    override fun reload(folder: Path) {
        this.folder = folder

        PlusDatabaseLoader.init(folder)

        this.factory = HibernateFactory.getSessionFactory()

        val currencies = folder.resolve("currencies")
        Files.createDirectories(currencies)
        for (entry in currencies.listDirectoryEntries()) {
            val currency = try {
                when {
                    entry.isDirectory() -> EconomyScriptCurrency.fromFolder(folder = entry)
                    entry.isReadable() -> EconomyScriptCurrency.fromZip(pack = entry)
                    else -> continue
                }
            } catch (_: NoSuchElementException) {
                continue
            }
            register(currency = currency, override = true)
        }
    }

    @Synchronized
    override fun flush() {
        this.factory.cache.evictAll()
    }

    @Synchronized
    override fun close() {
        this.factory.close()
    }

    // region Currency

    public override val hard: HardCurrencyDelegate = object : HardCurrencyDelegate {
        override fun getValue(thisRef: EconomyContext, property: KProperty<*>): EconomyCurrency {
            return fromSession { session ->
                val record = session.get(EconomyHardRecord::class.java, thisRef.id)
                    ?: return@fromSession MiraiCoin
                basket[record.currency]
                    ?: throw UnsupportedOperationException("找不到货币 ${record.currency}")
            }
        }

        override fun setValue(thisRef: EconomyContext, property: KProperty<*>, value: EconomyCurrency) {
            fromTransaction { session ->
                val record = EconomyHardRecord(
                    context = thisRef.id,
                    currency = value.id
                )
                session.merge(record)
            }
        }
    }

    // endregion

    // region Context

    override fun global(): GlobalEconomyContext {
        return PlusGlobalEconomyContext(session = factory.openSession(), service = this)
    }

    override fun custom(scope: CoroutineScope): GlobalEconomyContext {
        return PlusPluginEconomyContext(session = factory.openSession(), service = this, plugin = scope as JvmPlugin)
    }

    override fun context(target: Bot): BotEconomyContext {
        return PlusBotEconomyContext(session = factory.openSession(), service = this, bot = target)
    }

    override fun context(target: Group): GroupEconomyContext {
        return PlusGroupEconomyContext(session = factory.openSession(), service = this, group = target)
    }

    // endregion

    // region Account

    override fun account(user: User): UserEconomyAccount {
        return PlusUserEconomyAccount(
            record = fromTransaction { session ->
                val record = EconomyAccountRecord.fromUser(user = user)
                session.merge(record)
                session.flush()
                record
            },
            user = user
        )
    }

    override fun account(group: Group): GroupEconomyAccount {
        return PlusGroupEconomyAccount(
            record = fromTransaction { session ->
                val record = EconomyAccountRecord.fromGroup(group = group)
                session.merge(record)
                session.flush()
                record
            },
            group = group
        )
    }

    override fun account(uuid: String, description: String?): EconomyAccount {
        val record = fromTransaction { session ->
            val record = EconomyAccountRecord.fromInfo(uuid = uuid, description = description)
            session.merge(record)
            session.flush()
            record
        }
        val permitteeId = try {
            AbstractPermitteeId.parseFromString(record.uuid)
        } catch (_: IllegalStateException) {
            null
        }
        when (permitteeId) {
            is AbstractPermitteeId.ExactUser -> {
                for (bot in Bot.instances) {
                    val friend = bot.friends[permitteeId.id]
                    if (friend != null) return PlusUserEconomyAccount(record = record, user = friend)
                    for (group in bot.groups) {
                        val member = group.members[permitteeId.id]
                        if (member != null) return PlusUserEconomyAccount(record = record, user = member)
                    }
                }
            }
            is AbstractPermitteeId.ExactGroup -> {
                for (bot in Bot.instances) {
                    val group = bot.groups[permitteeId.groupId] ?: continue
                    return PlusGroupEconomyAccount(record = record, group = group)
                }
            }
            else -> Unit
        }

        return PlusCustomEconomyAccount(record = record)
    }

    // endregion
}
