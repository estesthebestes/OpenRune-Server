package org.rsmod.api.grandexchange.offer

/**
 * Packs an [OfferSlot] into [WORDS] ints so it can live in permanent server varps.
 *
 * Layout, least significant bit first: state (2), type (1), item (16), quantity (31), price (31),
 * completed quantity (31), completed gold (41) for 153 of the 160 available bits. The fee paid is
 * not part of the record; it has its own saturating int per slot (see [clampTax]).
 */
public object SlotCodec {
    public const val WORDS: Int = 5

    private const val STATE_BITS = 2
    private const val TYPE_BITS = 1
    private const val ITEM_BITS = 16
    private const val INT_BITS = 31
    private const val GOLD_BITS = 41

    public fun encode(slot: OfferSlot): IntArray {
        require(slot.itemId in 0..MAX_ITEM_ID) { "Item id out of range: ${slot.itemId}" }
        require(slot.quantity >= 0 && slot.price >= 0 && slot.completedQuantity >= 0)
        require(slot.completedGold in 0..OfferSlot.MAX_TOTAL) { "Gold out of range: ${slot.completedGold}" }
        val words = IntArray(WORDS)
        val writer = BitCursor(words)
        writer.write(slot.state.code.toLong(), STATE_BITS)
        writer.write(slot.type.code.toLong(), TYPE_BITS)
        writer.write(slot.itemId.toLong(), ITEM_BITS)
        writer.write(slot.quantity.toLong(), INT_BITS)
        writer.write(slot.price.toLong(), INT_BITS)
        writer.write(slot.completedQuantity.toLong(), INT_BITS)
        writer.write(slot.completedGold, GOLD_BITS)
        return words
    }

    public fun decode(words: IntArray, tax: Long = 0): OfferSlot {
        require(words.size >= WORDS) { "A slot record needs $WORDS words, got ${words.size}" }
        val reader = BitCursor(words.copyOf(WORDS))
        val state = OfferState.fromCode(reader.read(STATE_BITS).toInt())
        if (state == OfferState.EMPTY) {
            return OfferSlot.EMPTY
        }
        val type = OfferType.fromCode(reader.read(TYPE_BITS).toInt())
        val item = reader.read(ITEM_BITS).toInt()
        val quantity = reader.read(INT_BITS).toInt()
        val price = reader.read(INT_BITS).toInt()
        val completedQuantity = reader.read(INT_BITS).toInt()
        val gold = reader.read(GOLD_BITS)
        return OfferSlot(state, type, item, quantity, price, completedQuantity, gold, tax)
    }

    /** The fee is kept in a single signed int per slot, so very large totals saturate. */
    public fun clampTax(tax: Long): Int = tax.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    public const val MAX_ITEM_ID: Int = 0xFFFF

    private class BitCursor(private val words: IntArray) {
        private var position = 0

        fun write(value: Long, bits: Int) {
            var remaining = bits
            var v = value
            while (remaining > 0) {
                val word = position ushr 5
                val offset = position and 31
                val take = minOf(remaining, 32 - offset)
                val mask = (1L shl take) - 1
                words[word] = words[word] or (((v and mask) shl offset).toInt())
                v = v ushr take
                position += take
                remaining -= take
            }
        }

        fun read(bits: Int): Long {
            var out = 0L
            var done = 0
            while (done < bits) {
                val word = position ushr 5
                val offset = position and 31
                val take = minOf(bits - done, 32 - offset)
                val mask = (1L shl take) - 1
                val chunk = ((words[word].toLong() and 0xFFFFFFFFL) ushr offset) and mask
                out = out or (chunk shl done)
                position += take
                done += take
            }
            return out
        }
    }
}
