// port-lint: source flavors/never.rs
package io.github.kotlinmania.crossbeamchannel.flavors

import io.github.kotlinmania.crossbeamchannel.Context
import io.github.kotlinmania.crossbeamchannel.Operation
import io.github.kotlinmania.crossbeamchannel.RecvTimeoutError
import io.github.kotlinmania.crossbeamchannel.SelectHandle
import io.github.kotlinmania.crossbeamchannel.Token
import io.github.kotlinmania.crossbeamchannel.TryRecvError
import io.github.kotlinmania.crossbeamchannel.sleepUntil
import kotlin.time.Instant

/**
 * Channel that never delivers messages.
 */
internal class NeverChannel<T> : SelectHandle {

    fun tryRecv(): kotlin.Result<T> {
        return kotlin.Result.failure(TryRecvError.Empty)
    }

    fun recv(deadline: Instant?): kotlin.Result<T> {
        sleepUntil(deadline)
        return kotlin.Result.failure(RecvTimeoutError.Timeout)
    }

    fun read(token: Token): kotlin.Result<T> {
        return kotlin.Result.failure(IllegalStateException("never channel cannot be read"))
    }

    fun isEmpty(): Boolean = true

    fun isFull(): Boolean = true

    fun len(): Int = 0

    fun capacity(): Int? = 0

    override fun trySelect(token: Token): Boolean = false

    override fun deadline(): Instant? = null

    override fun register(oper: Operation, cx: Context): Boolean = isReady()

    override fun unregister(oper: Operation) {}

    override fun accept(token: Token, cx: Context): Boolean = trySelect(token)

    override fun isReady(): Boolean = false

    override fun watch(oper: Operation, cx: Context): Boolean = isReady()

    override fun unwatch(oper: Operation) {}

    companion object {
        fun <T> new(): NeverChannel<T> = NeverChannel()
    }
}
