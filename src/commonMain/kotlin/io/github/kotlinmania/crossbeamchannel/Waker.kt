// port-lint: source waker.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt

/**
 * Spinlock for multiplatform synchronization.
 */
internal class SpinLock {
    private val state = AtomicInt(0)

    inline fun <R> withLock(action: () -> R): R {
        val backoff = Backoff()
        while (!state.compareAndSet(0, 1)) {
            backoff.snooze()
        }
        try {
            return action()
        } finally {
            state.store(0)
        }
    }
}

/**
 * Represents a thread/task blocked on a specific channel operation.
 */
internal class Entry(
    val oper: Operation,
    val packet: Any?,
    val cx: Context
)

/**
 * A queue of operations blocked on channels.
 */
internal class Waker {
    val selectors = mutableListOf<Entry>()
    val observers = mutableListOf<Entry>()

    fun register(oper: Operation, cx: Context) {
        registerWithPacket(oper, null, cx)
    }

    fun registerWithPacket(oper: Operation, packet: Any?, cx: Context) {
        selectors.add(Entry(oper, packet, cx))
    }

    fun unregister(oper: Operation): Entry? {
        val index = selectors.indexOfFirst { it.oper == oper }
        return if (index >= 0) {
            selectors.removeAt(index)
        } else {
            null
        }
    }

    fun trySelect(callerContextId: Long = 0L): Entry? {
        if (selectors.isEmpty()) return null

        val index = selectors.indexOfFirst { entry ->
            entry.cx.id != callerContextId &&
                entry.cx.trySelectOrPrevious(Selected.Ready(entry.oper)) == null &&
                run {
                    entry.cx.storePacket(entry.packet)
                    entry.cx.unpark()
                    true
                }
        }

        return if (index >= 0) {
            selectors.removeAt(index)
        } else {
            null
        }
    }

    fun canSelect(callerContextId: Long = 0L): Boolean {
        if (selectors.isEmpty()) return false
        return selectors.any { entry ->
            entry.cx.id != callerContextId && entry.cx.selected() == Selected.Waiting
        }
    }

    fun watch(oper: Operation, cx: Context) {
        observers.add(Entry(oper, null, cx))
    }

    fun unwatch(oper: Operation) {
        observers.removeAll { it.oper == oper }
    }

    fun notifyObservers() {
        val copy = observers.toList()
        observers.clear()
        for (entry in copy) {
            if (entry.cx.trySelectOrPrevious(Selected.Ready(entry.oper)) == null) {
                entry.cx.unpark()
            }
        }
    }

    fun disconnect() {
        for (entry in selectors) {
            if (entry.cx.trySelectOrPrevious(Selected.Disconnected) == null) {
                entry.cx.unpark()
            }
        }
        notifyObservers()
    }
}

/**
 * A thread-safe waker that can be shared among threads.
 */
internal class SyncWaker {
    private val waker = Waker()
    private val lock = SpinLock()
    private val empty = AtomicBoolean(true)

    val isEmpty: Boolean
        get() = empty.load()

    fun register(oper: Operation, cx: Context) {
        lock.withLock {
            waker.register(oper, cx)
            empty.store(waker.selectors.isEmpty() && waker.observers.isEmpty())
        }
    }

    fun unregister(oper: Operation): Entry? {
        return lock.withLock {
            val entry = waker.unregister(oper)
            empty.store(waker.selectors.isEmpty() && waker.observers.isEmpty())
            entry
        }
    }

    fun notifyObservers(callerContextId: Long = 0L) {
        if (!empty.load()) {
            lock.withLock {
                if (!empty.load()) {
                    waker.trySelect(callerContextId)
                    waker.notifyObservers()
                    empty.store(waker.selectors.isEmpty() && waker.observers.isEmpty())
                }
            }
        }
    }

    fun watch(oper: Operation, cx: Context) {
        lock.withLock {
            waker.watch(oper, cx)
            empty.store(waker.selectors.isEmpty() && waker.observers.isEmpty())
        }
    }

    fun unwatch(oper: Operation) {
        lock.withLock {
            waker.unwatch(oper)
            empty.store(waker.selectors.isEmpty() && waker.observers.isEmpty())
        }
    }

    fun disconnect() {
        lock.withLock {
            waker.disconnect()
            empty.store(waker.selectors.isEmpty() && waker.observers.isEmpty())
        }
    }
}
