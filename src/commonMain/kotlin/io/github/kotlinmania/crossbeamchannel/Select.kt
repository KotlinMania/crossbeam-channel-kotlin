// port-lint: source select.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Temporary data that gets initialized during select or a blocking operation, and is consumed by read or write.
 */
public class Token {
    public var at: Instant? = null
    public var arraySlot: Int = -1
    public var arrayStamp: Long = 0L
    public var listBlock: Any? = null
    public var listOffset: Int = 0
    public var never: Unit? = null
    public var tick: Instant? = null
    public var zeroPacket: Any? = null
}

/**
 * A receiver or a sender that can participate in select.
 */
public interface SelectHandle {
    /**
     * Attempts to select an operation and returns `true` on success.
     */
    public fun trySelect(token: Token): Boolean

    /**
     * Returns a deadline for an operation, if there is one.
     */
    public fun deadline(): Instant?

    /**
     * Registers an operation for execution and returns `true` if it is now ready.
     */
    public fun register(oper: Operation, cx: Context): Boolean

    /**
     * Unregisters an operation for execution.
     */
    public fun unregister(oper: Operation)

    /**
     * Attempts to select an operation the thread got woken up for and returns `true` on success.
     */
    public fun accept(token: Token, cx: Context): Boolean

    /**
     * Returns `true` if an operation can be executed without blocking.
     */
    public fun isReady(): Boolean

    /**
     * Registers an operation for readiness notification and returns `true` if it is now ready.
     */
    public fun watch(oper: Operation, cx: Context): Boolean

    /**
     * Unregisters an operation for readiness notification.
     */
    public fun unwatch(oper: Operation)
}

/**
 * Represents a selected operation from `Select`.
 */
public class SelectedOperation internal constructor(
    internal val token: Token,
    private val selectedIndex: Int,
    internal val handle: SelectHandle
) {
    /**
     * Returns the index of the selected operation.
     */
    public fun index(): Int = selectedIndex

    /**
     * Completes the selected receive operation.
     */
    public fun <T> recv(receiver: Receiver<T>): kotlin.Result<T> {
        return receiver.read(token)
    }

    /**
     * Completes the selected send operation.
     */
    public fun <T> send(sender: Sender<T>, msg: T): SendOutcome<T> {
        return sender.write(token, msg)
    }
}

private sealed class Timeout {
    data object Now : Timeout()
    data object Never : Timeout()
    data class At(val deadline: Instant) : Timeout()
}

private data class HandleEntry(
    val handle: SelectHandle,
    val index: Int
)

private fun runSelect(
    handles: MutableList<HandleEntry>,
    timeout: Timeout,
    isBiased: Boolean
): SelectedOperation? {
    if (handles.isEmpty()) {
        when (timeout) {
            is Timeout.Now -> return null
            is Timeout.Never -> {
                sleepUntil(null)
                throw IllegalStateException("unreachable")
            }
            is Timeout.At -> {
                sleepUntil(timeout.deadline)
                return null
            }
        }
    }

    if (!isBiased) {
        shuffle(handles)
    }

    val token = Token()

    for (entry in handles) {
        if (entry.handle.trySelect(token)) {
            return SelectedOperation(token, entry.index, entry.handle)
        }
    }

    while (true) {
        val res = Context.with<SelectedOperation?> { cx ->
            var sel: Selected = Selected.Waiting
            var registeredCount = 0
            var indexReady: Int? = null

            if (timeout is Timeout.Now) {
                cx.trySelect(Selected.Aborted)
            }

            for (entry in handles) {
                registeredCount++
                val oper = Operation(entry.index.toLong() + 10L)
                if (entry.handle.register(oper, cx)) {
                    sel = when (val prev = cx.trySelectOrPrevious(Selected.Aborted)) {
                        null -> {
                            indexReady = entry.index
                            Selected.Aborted
                        }
                        else -> prev
                    }
                    break
                }

                sel = cx.selected()
                if (sel != Selected.Waiting) {
                    break
                }
            }

            if (sel == Selected.Waiting) {
                var deadline: Instant? = when (timeout) {
                    is Timeout.Now -> return@with null
                    is Timeout.Never -> null
                    is Timeout.At -> timeout.deadline
                }

                for (entry in handles) {
                    val d = entry.handle.deadline()
                    if (d != null) {
                        deadline = if (deadline == null || d < deadline) d else deadline
                    }
                }

                sel = cx.waitUntil(deadline)
            }

            for (i in 0 until registeredCount) {
                val entry = handles[i]
                val oper = Operation(entry.index.toLong() + 10L)
                entry.handle.unregister(oper)
            }

            when (sel) {
                is Selected.Waiting -> throw IllegalStateException("unreachable")
                is Selected.Aborted -> {
                    if (indexReady != null) {
                        for (entry in handles) {
                            if (entry.index == indexReady && entry.handle.trySelect(token)) {
                                return@with SelectedOperation(token, entry.index, entry.handle)
                            }
                        }
                    }
                    null
                }
                is Selected.Disconnected -> null
                is Selected.Ready -> {
                    for (entry in handles) {
                        val oper = Operation(entry.index.toLong() + 10L)
                        if (sel.operation == oper) {
                            if (entry.handle.accept(token, cx)) {
                                return@with SelectedOperation(token, entry.index, entry.handle)
                            }
                        }
                    }
                    null
                }
            }
        }

        if (res != null) {
            return res
        }

        for (entry in handles) {
            if (entry.handle.trySelect(token)) {
                return SelectedOperation(token, entry.index, entry.handle)
            }
        }

        when (timeout) {
            is Timeout.Now -> return null
            is Timeout.Never -> {}
            is Timeout.At -> {
                if (now() >= timeout.deadline) {
                    return null
                }
            }
        }
    }
}

private fun runReady(
    handles: MutableList<HandleEntry>,
    timeout: Timeout,
    isBiased: Boolean
): Int? {
    if (handles.isEmpty()) {
        when (timeout) {
            is Timeout.Now -> return null
            is Timeout.Never -> {
                sleepUntil(null)
                throw IllegalStateException("unreachable")
            }
            is Timeout.At -> {
                sleepUntil(timeout.deadline)
                return null
            }
        }
    }

    if (!isBiased) {
        shuffle(handles)
    }

    for (entry in handles) {
        if (entry.handle.isReady()) {
            return entry.index
        }
    }

    while (true) {
        val res = Context.with<Int?> { cx ->
            var sel: Selected = Selected.Waiting
            var registeredCount = 0
            var indexReady: Int? = null

            if (timeout is Timeout.Now) {
                cx.trySelect(Selected.Aborted)
            }

            for (entry in handles) {
                registeredCount++
                val oper = Operation(entry.index.toLong() + 10L)
                if (entry.handle.watch(oper, cx)) {
                    sel = when (val prev = cx.trySelectOrPrevious(Selected.Aborted)) {
                        null -> {
                            indexReady = entry.index
                            Selected.Aborted
                        }
                        else -> prev
                    }
                    break
                }

                sel = cx.selected()
                if (sel != Selected.Waiting) {
                    break
                }
            }

            if (sel == Selected.Waiting) {
                var deadline: Instant? = when (timeout) {
                    is Timeout.Now -> return@with null
                    is Timeout.Never -> null
                    is Timeout.At -> timeout.deadline
                }

                for (entry in handles) {
                    val d = entry.handle.deadline()
                    if (d != null) {
                        deadline = if (deadline == null || d < deadline) d else deadline
                    }
                }

                sel = cx.waitUntil(deadline)
            }

            for (i in 0 until registeredCount) {
                val entry = handles[i]
                val oper = Operation(entry.index.toLong() + 10L)
                entry.handle.unwatch(oper)
            }

            when (sel) {
                is Selected.Waiting -> throw IllegalStateException("unreachable")
                is Selected.Aborted -> {
                    if (indexReady != null) {
                        return@with indexReady
                    }
                    null
                }
                is Selected.Disconnected -> null
                is Selected.Ready -> {
                    for (entry in handles) {
                        val oper = Operation(entry.index.toLong() + 10L)
                        if (sel.operation == oper) {
                            return@with entry.index
                        }
                    }
                    null
                }
            }
        }

        if (res != null) {
            return res
        }

        for (entry in handles) {
            if (entry.handle.isReady()) {
                return entry.index
            }
        }

        when (timeout) {
            is Timeout.Now -> return null
            is Timeout.Never -> {}
            is Timeout.At -> {
                if (now() >= timeout.deadline) {
                    return null
                }
            }
        }
    }
}

/**
 * Selects from a set of channel operations.
 */
public class Select {
    private val handles = mutableListOf<HandleEntry>()
    private var nextIndex = 0
    private var biased = false

    public constructor()

    public constructor(biased: Boolean) {
        this.biased = biased
    }

    /**
     * Adds a send operation.
     */
    public fun <T> send(s: Sender<T>): Int {
        val i = nextIndex++
        handles.add(HandleEntry(s, i))
        return i
    }

    /**
     * Adds a receive operation.
     */
    public fun <T> recv(r: Receiver<T>): Int {
        val i = nextIndex++
        handles.add(HandleEntry(r, i))
        return i
    }

    /**
     * Removes a previously added operation.
     */
    public fun remove(index: Int) {
        val pos = handles.indexOfFirst { it.index == index }
        if (pos < 0) {
            throw IllegalArgumentException("no operation with this index: $index")
        }
        handles.removeAt(pos)
    }

    /**
     * Attempts to select one of the operations without blocking.
     */
    public fun trySelect(): kotlin.Result<SelectedOperation> {
        val copy = handles.toMutableList()
        val op = runSelect(copy, Timeout.Now, biased)
        return if (op != null) {
            kotlin.Result.success(op)
        } else {
            kotlin.Result.failure(TrySelectError())
        }
    }

    /**
     * Blocks until one of the operations becomes ready and selects it.
     */
    public fun select(): SelectedOperation {
        if (handles.isEmpty()) {
            throw IllegalStateException("no operations have been added to Select")
        }
        val copy = handles.toMutableList()
        return runSelect(copy, Timeout.Never, biased)
            ?: throw IllegalStateException("select failed")
    }

    /**
     * Blocks for a limited time until one of the operations becomes ready and selects it.
     */
    public fun selectTimeout(timeout: Duration): kotlin.Result<SelectedOperation> {
        val deadline = now() + timeout
        return selectDeadline(deadline)
    }

    /**
     * Blocks until a given deadline, or until one of the operations becomes ready and selects it.
     */
    public fun selectDeadline(deadline: Instant): kotlin.Result<SelectedOperation> {
        val copy = handles.toMutableList()
        val op = runSelect(copy, Timeout.At(deadline), biased)
        return if (op != null) {
            kotlin.Result.success(op)
        } else {
            kotlin.Result.failure(SelectTimeoutError())
        }
    }

    /**
     * Attempts to find a ready operation without blocking.
     */
    public fun tryReady(): kotlin.Result<Int> {
        val copy = handles.toMutableList()
        val idx = runReady(copy, Timeout.Now, biased)
        return if (idx != null) {
            kotlin.Result.success(idx)
        } else {
            kotlin.Result.failure(TryReadyError())
        }
    }

    /**
     * Blocks until one of the operations becomes ready.
     */
    public fun ready(): Int {
        if (handles.isEmpty()) {
            throw IllegalStateException("no operations have been added to Select")
        }
        val copy = handles.toMutableList()
        return runReady(copy, Timeout.Never, biased)
            ?: throw IllegalStateException("ready failed")
    }

    /**
     * Blocks for a limited time until one of the operations becomes ready.
     */
    public fun readyTimeout(timeout: Duration): kotlin.Result<Int> {
        val deadline = now() + timeout
        return readyDeadline(deadline)
    }

    /**
     * Blocks until a given deadline, or until one of the operations becomes ready.
     */
    public fun readyDeadline(deadline: Instant): kotlin.Result<Int> {
        val copy = handles.toMutableList()
        val idx = runReady(copy, Timeout.At(deadline), biased)
        return if (idx != null) {
            kotlin.Result.success(idx)
        } else {
            kotlin.Result.failure(ReadyTimeoutError())
        }
    }

    public companion object {
        /**
         * Creates an empty list of channel operations for selection.
         */
        public fun new(): Select = Select()

        /**
         * Creates an empty list of channel operations with biased selection.
         */
        public fun newBiased(): Select = Select(biased = true)
    }
}
