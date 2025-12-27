package xyz.cssxsh.mirai.economy.console.plus

import jakarta.persistence.*
import net.mamoe.mirai.utils.*
import org.hibernate.*
import xyz.cssxsh.mirai.economy.console.entity.*
import xyz.cssxsh.mirai.economy.event.*
import xyz.cssxsh.mirai.economy.service.*
import java.io.Flushable

@PublishedApi
internal abstract class PlusSessionAction : Flushable, AutoCloseable, EconomyAction {

    // region EconomyContext

    abstract val logger: MiraiLogger
    abstract val session: Session
    protected abstract val context: String
    abstract val service: PlusEconomyService

    override fun flush() {
        if (session.isJoinedToTransaction) session.flush()
    }

    override fun close() {
        session.close()
    }

    // endregion

    open fun <T> transaction(block: (Session) -> T) {
        val transaction = session.beginTransaction()
        try {
            block.invoke(session)
            transaction.commit()
        } catch (exception: RollbackException) {
            logger.error({ "事务提交异常" }, exception)
            try {
                transaction.rollback()
            } catch (cause: PersistenceException) {
                logger.warning({ "回滚异常" }, cause)
            }
        }
    }

    override fun EconomyAccount.get(currency: EconomyCurrency): Double {
        val index = EconomyAccountIndex(
            uuid = uuid,
            currency = currency.id,
            context = context
        )
        val record = session.get(EconomyBalanceRecord::class.java, index)
        return record?.balance ?: 0.0
    }

    override fun EconomyAccount.balance(): Map<EconomyCurrency, Double> {
        val hql = "FROM EconomyBalanceRecord r WHERE r.index.uuid = :uuid AND r.index.context = :context"
        val query = session.createQuery(hql, EconomyBalanceRecord::class.java)
        query.setParameter("uuid", uuid)
        query.setParameter("context", context)
        val records = query.list()

        return buildMap {
            for (record in records) {
                val currency = service.basket[record.index.currency] ?: continue
                put(currency, record.balance)
            }
        }
    }

    override fun EconomyAccount.set(currency: EconomyCurrency, quantity: Double) = synchronized(currency) {
        val index = EconomyAccountIndex(
            uuid = uuid,
            currency = currency.id,
            context = context
        )
        val record = session.get(EconomyBalanceRecord::class.java, index) ?: EconomyBalanceRecord(
            index = index,
            balance = 0.0
        )
        val event = EconomyBalanceChangeEvent(
            account = this,
            service = service,
            currency = currency,
            current = record.balance,
            change = quantity,
            mode = EconomyBalanceChangeMode.SET
        )
        service.broadcast(event) {
            transaction { session ->
                session.merge(record.copy(balance = quantity))
            }
        }
    }

    override fun EconomyAccount.plusAssign(currency: EconomyCurrency, quantity: Double) = synchronized(currency) {
        val index = EconomyAccountIndex(
            uuid = uuid,
            currency = currency.id,
            context = context
        )
        val record = session.get(EconomyBalanceRecord::class.java, index) ?: EconomyBalanceRecord(
            index = index,
            balance = 0.0
        )
        val event = EconomyBalanceChangeEvent(
            account = this,
            service = service,
            currency = currency,
            current = record.balance,
            change = quantity,
            mode = EconomyBalanceChangeMode.PLUS
        )
        service.broadcast(event) {
            transaction { session ->
                session.merge(record.copy(balance = current + change))
            }
        }
    }

    override fun EconomyAccount.minusAssign(currency: EconomyCurrency, quantity: Double) = synchronized(currency) {
        val index = EconomyAccountIndex(
            uuid = uuid,
            currency = currency.id,
            context = context
        )
        val record = session.get(EconomyBalanceRecord::class.java, index) ?: EconomyBalanceRecord(
            index = index,
            balance = 0.0
        )
        val event = EconomyBalanceChangeEvent(
            account = this,
            service = service,
            currency = currency,
            current = record.balance,
            change = quantity,
            mode = EconomyBalanceChangeMode.MINUS
        )
        service.broadcast(event) {
            transaction { session ->
                session.merge(record.copy(balance = current - quantity))
            }
        }
    }

    override fun EconomyAccount.timesAssign(currency: EconomyCurrency, quantity: Double) = synchronized(currency) {
        val index = EconomyAccountIndex(
            uuid = uuid,
            currency = currency.id,
            context = context
        )
        val record = session.get(EconomyBalanceRecord::class.java, index) ?: EconomyBalanceRecord(
            index = index,
            balance = 0.0
        )
        val event = EconomyBalanceChangeEvent(
            account = this,
            service = service,
            currency = currency,
            current = record.balance,
            change = quantity,
            mode = EconomyBalanceChangeMode.TIMES
        )
        service.broadcast(event) {
            transaction { session ->
                session.merge(record.copy(balance = current * change))
            }
        }
    }

    override fun EconomyAccount.divAssign(currency: EconomyCurrency, quantity: Double) = synchronized(currency) {
        val index = EconomyAccountIndex(
            uuid = uuid,
            currency = currency.id,
            context = context
        )
        val record = session.get(EconomyBalanceRecord::class.java, index) ?: EconomyBalanceRecord(
            index = index,
            balance = 0.0
        )
        val event = EconomyBalanceChangeEvent(
            account = this,
            service = service,
            currency = currency,
            current = record.balance,
            change = quantity,
            mode = EconomyBalanceChangeMode.DIV
        )
        service.broadcast(event) {
            transaction { session ->
                session.merge(record.copy(balance = current / change))
            }
        }
    }

    override fun EconomyCurrency.balance(): Map<EconomyAccount, Double> {
        val hql = "FROM EconomyBalanceRecord r WHERE r.index.currency = :currency AND r.index.context = :context"
        val query = session.createQuery(hql, EconomyBalanceRecord::class.java)
        query.setParameter("currency", id)
        query.setParameter("context", context)
        val records = query.list()

        return buildMap {
            for (record in records) {
                val account = try {
                    service.account(uuid = record.index.uuid)
                } catch (cause: NoSuchElementException) {
                    logger.warning({ "找不到用户 ${record.index.uuid}" }, cause)
                    continue
                }
                put(account, record.balance)
            }
        }
    }

    override fun EconomyCurrency.transaction(block: EconomyTransaction.() -> Unit) = synchronized(this@transaction) {
        val transaction = EconomyTransaction(
            context = this@PlusSessionAction,
            currency = this,
            balance = java.util.concurrent.ConcurrentHashMap(this.balance())
        )
        block.invoke(transaction)
        val event = EconomyCurrencyTransactionEvent(
            service = service,
            transaction = transaction
        )
        service.broadcast(event) {
            transaction { session ->
                for ((account, balance) in transaction) {
                    val index = EconomyAccountIndex(
                        uuid = account.uuid,
                        currency = currency.id,
                        context = context
                    )
                    val record = EconomyBalanceRecord(
                        index = index,
                        balance = balance
                    )
                    session.merge(record)
                }
            }
        }
    }
}

