// port-lint: source select_macro.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.time.Duration

/**
 * DSL scope for building `select` operations.
 */
public class SelectScope {
    @PublishedApi
    internal val select = Select.new()

    @PublishedApi
    internal val callbacks = mutableListOf<(SelectedOperation) -> Unit>()

    /**
     * Adds a receive operation to the select block.
     */
    public fun <T> onRecv(receiver: Receiver<T>, block: (kotlin.Result<T>) -> Unit) {
        select.recv(receiver)
        callbacks.add { op ->
            val res = op.recv(receiver)
            block(res)
        }
    }

    /**
     * Adds a send operation to the select block.
     */
    public fun <T> onSend(sender: Sender<T>, msg: T, block: (SendOutcome<T>) -> Unit) {
        select.send(sender)
        callbacks.add { op ->
            val res = op.send(sender, msg)
            block(res)
        }
    }

    /**
     * Executes the select block, waiting until one operation is ready.
     */
    public fun execute() {
        val op = select.select()
        val callback = callbacks.getOrNull(op.index())
        callback?.invoke(op)
    }

    /**
     * Executes the select block with a timeout.
     */
    public fun executeTimeout(timeout: Duration): Boolean {
        val res = select.selectTimeout(timeout)
        return if (res.isSuccess) {
            val op = res.getOrNull()
            if (op != null) {
                val callback = callbacks.getOrNull(op.index())
                callback?.invoke(op)
                true
            } else {
                false
            }
        } else {
            false
        }
    }
}

/**
 * Executes a selection block over channels.
 */
public inline fun select(builder: SelectScope.() -> Unit) {
    val scope = SelectScope()
    scope.builder()
    scope.execute()
}
