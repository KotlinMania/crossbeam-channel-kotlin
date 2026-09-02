// port-lint: tests tests/select.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectTest {
    @Test
    fun smoke1() {
        val (s1, r1) = unbounded<Int>()
        val (s2, r2) = unbounded<Int>()

        s1.send(1)

        val sel1 = Select()
        val oper1_1 = sel1.recv(r1)
        val oper2_1 = sel1.recv(r2)
        val op1 = sel1.select()
        assertEquals(oper1_1, op1.index())
        assertEquals(Result.success(1), op1.recv(r1))

        s2.send(2)

        val sel2 = Select()
        val oper1_2 = sel2.recv(r1)
        val oper2_2 = sel2.recv(r2)
        val op2 = sel2.select()
        assertEquals(oper2_2, op2.index())
        assertEquals(Result.success(2), op2.recv(r2))
    }

    @Test
    fun smoke2() {
        val (s1, r1) = unbounded<Int>()
        val (s2, r2) = unbounded<Int>()
        val (s3, r3) = unbounded<Int>()
        val (s4, r4) = unbounded<Int>()
        val (s5, r5) = unbounded<Int>()

        s5.send(5)

        val sel = Select()
        sel.recv(r1)
        sel.recv(r2)
        sel.recv(r3)
        sel.recv(r4)
        val oper5 = sel.recv(r5)
        val op = sel.select()
        assertEquals(oper5, op.index())
        assertEquals(Result.success(5), op.recv(r5))
    }

    @Test
    fun defaultWhenDisconnected() {
        val (s1, r1) = unbounded<Int>()
        s1.release()

        val sel1 = Select()
        val oper1 = sel1.recv(r1)
        val op1 = sel1.trySelect()
        assertTrue(op1.isSuccess)
        val selected1 = op1.getOrNull()
        assertEquals(oper1, selected1?.index())
        assertTrue(selected1?.recv(r1)?.isFailure == true)

        val (s2, r2) = bounded<Int>(0)
        r2.release()

        val sel2 = Select()
        val oper2 = sel2.send(s2)
        val op2 = sel2.trySelect()
        assertTrue(op2.isSuccess)
        val selected2 = op2.getOrNull()
        assertEquals(oper2, selected2?.index())
        val sendRes = selected2?.send(s2, 0)
        assertTrue(sendRes is SendOutcome.Err)
    }

    @Test
    fun nesting() {
        val (s, r) = unbounded<Int>()

        val sel1 = Select()
        val oper1 = sel1.send(s)
        val op1 = sel1.select()
        assertEquals(oper1, op1.index())
        assertEquals(SendOutcome.Ok, op1.send(s, 0))

        val sel2 = Select()
        val oper2 = sel2.recv(r)
        val op2 = sel2.select()
        assertEquals(oper2, op2.index())
        assertEquals(Result.success(0), op2.recv(r))

        val sel3 = Select()
        val oper3 = sel3.send(s)
        val op3 = sel3.select()
        assertEquals(oper3, op3.index())
        assertEquals(SendOutcome.Ok, op3.send(s, 1))

        val sel4 = Select()
        val oper4 = sel4.recv(r)
        val op4 = sel4.select()
        assertEquals(oper4, op4.index())
        assertEquals(Result.success(1), op4.recv(r))
    }
}
