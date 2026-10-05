package dev.openrune.cs2

import dev.openrune.DirectoryConstants
import dev.openrune.cache.tools.TaskPriority
import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.filesystem.Cache
import java.io.File
import java.nio.ByteBuffer
import kotlin.io.path.exists
import org.slf4j.LoggerFactory

/**
 * The CS2 compiler emits `push_var` / `pop_var` for every varp, but the client reads and writes
 * long varps through `push_var_long` / `pop_var_long`. A script that touches a long varp the
 * compiler's way hits an empty long stack and aborts, so this pass rewrites those opcodes in the
 * packed scripts after the compiler has run.
 */
public class LongVarpPatch(private val symbolsDir: File = defaultSymbolsDir()) : CacheTask() {
    private val logger = LoggerFactory.getLogger(LongVarpPatch::class.java)

    override val priority: TaskPriority
        get() = TaskPriority.CS2

    override fun init(cache: Cache) {
        val longVarps = readLongVarps(symbolsDir)
        if (longVarps.isEmpty()) {
            logger.warn("No long varps found in {}; skipping long varp patch", symbolsDir)
            return
        }
        var patched = 0
        for (script in cache.archives(SCRIPT_INDEX)) {
            val data = cache.data(SCRIPT_INDEX, script) ?: continue
            val rewritten = patch(data, longVarps) ?: continue
            cache.write(SCRIPT_INDEX, script, rewritten)
            patched++
        }
        logger.info("Patched {} scripts to read long varps through push_var_long", patched)
    }

    public companion object {
        public const val SCRIPT_INDEX: Int = 12
        public const val PUSH_VAR: Int = 1
        public const val POP_VAR: Int = 2
        public const val PUSH_VAR_LONG: Int = 64
        public const val POP_VAR_LONG: Int = 65
        private const val PUSH_CONSTANT_STRING = 3
        private const val PUSH_CONSTANT_LONG = 61
        private val BYTE_OPERAND_OPS = setOf(21, 38, 39, 62, 63)
        private val FOOTER_SIZES = intArrayOf(16, 12)

        private fun defaultSymbolsDir(): File = DirectoryConstants.CS2_PATH.resolve("symbols").toFile()

        public fun readLongVarps(symbolsDir: File): Set<Int> {
            val file = File(symbolsDir, "varp.sym")
            if (!file.toPath().exists()) return emptySet()
            return file
                .readLines()
                .mapNotNull { line ->
                    val cols = line.split('\t')
                    if (cols.size >= 3 && cols[2].trim() == "long") cols[0].toIntOrNull() else null
                }
                .toSet()
        }

        /** Returns the rewritten script, or null when nothing needed changing or it can't be parsed. */
        public fun patch(script: ByteArray, longVarps: Set<Int>): ByteArray? {
            val nameEnd = script.indexOf(0.toByte())
            if (nameEnd < 0 || script.size < nameEnd + 1 + 2) return null
            val buf = ByteBuffer.wrap(script)
            val switchLength = buf.getShort(script.size - 2).toInt() and 0xFFFF
            val footerEnd = script.size - 2 - switchLength
            if (footerEnd < nameEnd + 1) return null

            for (footerSize in FOOTER_SIZES) {
                val footerStart = footerEnd - footerSize
                if (footerStart < nameEnd + 1) continue
                val count = buf.getInt(footerStart)
                val patches = scan(buf, nameEnd + 1, footerStart, count, longVarps) ?: continue
                if (patches.isEmpty()) return null
                val out = script.copyOf()
                for (offset in patches) {
                    val opcode = ((out[offset].toInt() and 0xFF) shl 8) or (out[offset + 1].toInt() and 0xFF)
                    val long = if (opcode == PUSH_VAR) PUSH_VAR_LONG else POP_VAR_LONG
                    out[offset] = (long shr 8).toByte()
                    out[offset + 1] = long.toByte()
                }
                return out
            }
            return null
        }

        private fun scan(
            buf: ByteBuffer,
            start: Int,
            limit: Int,
            count: Int,
            longVarps: Set<Int>,
        ): List<Int>? {
            if (count < 0) return null
            val patches = ArrayList<Int>()
            var pos = start
            repeat(count) {
                if (pos + 2 > limit) return null
                val opcode = buf.getShort(pos).toInt() and 0xFFFF
                val operandAt = pos + 2
                pos = operandAt
                pos +=
                    when {
                        opcode == PUSH_CONSTANT_STRING -> {
                            var end = pos
                            while (end < limit && buf.get(end) != 0.toByte()) end++
                            if (end >= limit) return null
                            end + 1 - pos
                        }
                        opcode == PUSH_CONSTANT_LONG -> 8
                        opcode in BYTE_OPERAND_OPS || opcode >= 100 -> 1
                        else -> 4
                    }
                if (pos > limit) return null
                if ((opcode == PUSH_VAR || opcode == POP_VAR) && buf.getInt(operandAt) in longVarps) {
                    patches.add(operandAt - 2)
                }
            }
            return if (pos == limit) patches else null
        }
    }
}
