// port-lint: ignore — Kotlin smoke tests for the ported err.rs error types
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ErrTest {
    @Test
    fun sendErrorPreservesMessageAndUnwraps() {
        val err = SendError("payload")
        assertEquals("payload", err.intoInner())
        assertEquals("sending on a disconnected channel", err.message)
        assertEquals("SendError(..)", err.toString())
    }

    @Test
    fun sendErrorEqualityIsStructural() {
        assertEquals(SendError("x"), SendError("x"))
        assertNotEquals(SendError("x"), SendError("y"))
    }

    @Test
    fun trySendErrorFullVsDisconnected() {
        val full: TrySendError<Int> = TrySendError.Full(1)
        val disc: TrySendError<Int> = TrySendError.Disconnected(1)
        assertTrue(full.isFull())
        assertFalse(full.isDisconnected())
        assertFalse(disc.isFull())
        assertTrue(disc.isDisconnected())
        assertEquals(1, full.intoInner())
        assertEquals(1, disc.intoInner())
        assertEquals("Full(..)", full.toString())
        assertEquals("Disconnected(..)", disc.toString())
        assertEquals("sending on a full channel", full.message)
        assertEquals("sending on a disconnected channel", disc.message)
        assertNotEquals<TrySendError<Int>>(full, disc)
    }

    @Test
    fun sendErrorConvertsToTrySendErrorDisconnected() {
        val converted = SendError("v").toTrySendError()
        assertTrue(converted is TrySendError.Disconnected<String>)
        assertEquals("v", converted.intoInner())
    }

    @Test
    fun sendTimeoutErrorVariants() {
        val to: SendTimeoutError<Int> = SendTimeoutError.Timeout(7)
        val disc: SendTimeoutError<Int> = SendTimeoutError.Disconnected(7)
        assertTrue(to.isTimeout())
        assertFalse(to.isDisconnected())
        assertTrue(disc.isDisconnected())
        assertEquals("timed out waiting on send operation", to.message)
        assertEquals("sending on a disconnected channel", disc.message)
        assertEquals("SendTimeoutError(..)", to.toString())
    }

    @Test
    fun sendErrorConvertsToSendTimeoutErrorDisconnected() {
        val converted = SendError(42).toSendTimeoutError()
        assertTrue(converted is SendTimeoutError.Disconnected<Int>)
        assertEquals(42, converted.intoInner())
    }

    @Test
    fun recvErrorMessage() {
        val err = RecvError()
        assertEquals("receiving on an empty and disconnected channel", err.message)
        assertEquals(RecvError(), err)
    }

    @Test
    fun tryRecvErrorPredicates() {
        val empty: TryRecvError = TryRecvError.Empty
        val disc: TryRecvError = TryRecvError.Disconnected
        assertTrue(empty.isEmpty())
        assertFalse(empty.isDisconnected())
        assertTrue(disc.isDisconnected())
        assertFalse(disc.isEmpty())
        assertEquals("receiving on an empty channel", empty.message)
        assertEquals("receiving on an empty and disconnected channel", disc.message)
    }

    @Test
    fun recvErrorConvertsToTryRecvErrorDisconnected() {
        assertEquals(TryRecvError.Disconnected, RecvError().toTryRecvError())
    }

    @Test
    fun recvTimeoutErrorPredicates() {
        val timeout: RecvTimeoutError = RecvTimeoutError.Timeout
        val disc: RecvTimeoutError = RecvTimeoutError.Disconnected
        assertTrue(timeout.isTimeout())
        assertFalse(timeout.isDisconnected())
        assertTrue(disc.isDisconnected())
        assertEquals("timed out waiting on receive operation", timeout.message)
        assertEquals("channel is empty and disconnected", disc.message)
    }

    @Test
    fun recvErrorConvertsToRecvTimeoutErrorDisconnected() {
        assertEquals(RecvTimeoutError.Disconnected, RecvError().toRecvTimeoutError())
    }

    @Test
    fun selectErrorMessages() {
        assertEquals("all operations in select would block", TrySelectError().message)
        assertEquals("timed out waiting on select", SelectTimeoutError().message)
        assertEquals("all operations in select would block", TryReadyError().message)
        assertEquals("timed out waiting on select", ReadyTimeoutError().message)
    }
}
