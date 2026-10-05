package dev.openrune.cs2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LongVarpPatchTest {
    private val taxVarps = (5754..5761).toSet()

    @Test
    fun `rewrites reads of long varps in a compiled script`() {
        val patched = requireNotNull(LongVarpPatch.patch(SCRIPT_5732, taxVarps))

        assertEquals(SCRIPT_5732.size, patched.size)
        assertEquals(8, differingBytes(SCRIPT_5732, patched))
        for (varp in taxVarps) {
            assertEquals(1, countOf(SCRIPT_5732, pushVar(1, varp)))
            assertEquals(1, countOf(patched, pushVar(LongVarpPatch.PUSH_VAR_LONG, varp)))
            assertEquals(0, countOf(patched, pushVar(LongVarpPatch.PUSH_VAR, varp)))
        }
    }

    @Test
    fun `patching is idempotent`() {
        val once = requireNotNull(LongVarpPatch.patch(SCRIPT_5732, taxVarps))
        assertNull(LongVarpPatch.patch(once, taxVarps))
    }

    @Test
    fun `leaves scripts that touch no long varp alone`() {
        assertNull(LongVarpPatch.patch(SCRIPT_5732, setOf(1, 2, 3)))
    }

    @Test
    fun `rewrites writes as well as reads`() {
        val script = script(op(LongVarpPatch.POP_VAR, 5753), op(LongVarpPatch.PUSH_VAR, 1151))
        val patched = requireNotNull(LongVarpPatch.patch(script, setOf(5753)))

        assertEquals(1, countOf(patched, pushVar(LongVarpPatch.POP_VAR_LONG, 5753)))
        assertEquals(1, countOf(patched, pushVar(LongVarpPatch.PUSH_VAR, 1151)))
    }

    @Test
    fun `skips a script whose body does not parse`() {
        assertNull(LongVarpPatch.patch(SCRIPT_5732.copyOf(SCRIPT_5732.size - 20), taxVarps))
        assertNull(LongVarpPatch.patch(byteArrayOf(0, 0, 0), taxVarps))
    }

    private fun op(opcode: Int, operand: Int): ByteArray =
        pushVar(opcode, operand)

    private fun pushVar(opcode: Int, varp: Int): ByteArray =
        byteArrayOf(
            (opcode shr 8).toByte(),
            opcode.toByte(),
            (varp shr 24).toByte(),
            (varp shr 16).toByte(),
            (varp shr 8).toByte(),
            varp.toByte(),
        )

    private fun script(vararg ops: ByteArray): ByteArray {
        val name = "t".toByteArray() + 0
        val body = ops.fold(ByteArray(0)) { acc, op -> acc + op }
        val footer = ByteArray(16).also { it[3] = ops.size.toByte() }
        return name + body + footer + byteArrayOf(0, 0)
    }

    private fun differingBytes(a: ByteArray, b: ByteArray): Int =
        a.indices.count { a[it] != b[it] }

    private fun countOf(haystack: ByteArray, needle: ByteArray): Int {
        var count = 0
        for (i in 0..haystack.size - needle.size) {
            if (haystack.copyOfRange(i, i + needle.size).contentEquals(needle)) count++
        }
        return count
    }

    private companion object {
        private fun hex(value: String): ByteArray =
            ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        val SCRIPT_5732: ByteArray = hex(
            "5b70726f632c736372697074353733325d2e637332205b70726f632c736372697074353733325d00" +
                "002100000000003c00000000000600000027003d000000000000000000010000167a0fca00001500" +
                "000600000022003d000000000000000000010000167b0fca0000150000060000001d003d00000000" +
                "0000000000010000167c0fca00001500000600000018003d000000000000000000010000167d0fca" +
                "00001500000600000013003d000000000000000000010000167e0fca0000150000060000000e003d" +
                "000000000000000000010000167f0fca00001500000600000009003d000000000000000000010000" +
                "16800fca00001500000600000004003d00000000000000000001000016810fca00001500003d0000" +
                "000000000000001500003dffffffffffffffff0015000000002e0001000000000001000000000100" +
                "0800000000000000010000000100000006000000020000000b000000030000001000000004000000" +
                "15000000050000001a000000060000001f00000007000000240043",
        )
    }
}
