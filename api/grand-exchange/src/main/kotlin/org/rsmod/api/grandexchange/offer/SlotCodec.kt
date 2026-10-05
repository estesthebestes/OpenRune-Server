package org.rsmod.api.grandexchange.offer

/**
 * Packs the eight [OfferSlot]s of a player into [WORDS] ints so they can live in permanent server
 * varps.
 *
 * Every slot takes [SLOT_BITS] bits, least significant bit first: state (2), type (1), item (16),
 * quantity (31), price (31), completed quantity (31), completed gold (41). Slots follow each other
 * with no padding, so a record may straddle two ints. The fee paid is not part of the record; it
 * has its own saturating int per slot (see [clampTax]).
 */
public object SlotCodec {
    public const val SLOTS: Int = 8
    public const val MAX_ITEM_ID: Int = 0xFFFF

    private const val STATE_BITS = 2
    private const val TYPE_BITS = 1
    private const val ITEM_BITS = 16
    private const val INT_BITS = 31
    private const val GOLD_BITS = 41

    public const val SLOT_BITS: Int =
        STATE_BITS + TYPE_BITS + ITEM_BITS + 3 * INT_BITS + GOLD_BITS

    public const val WORDS: Int = (SLOT_BITS * SLOTS + 31) / 32

    /** The (0-based) range of words a slot's record touches. */
    public fun wordsOf(slotIndex: Int): IntRange {
        val start = slotIndex * SLOT_BITS
        return (start ushr 5)..((start + SLOT_BITS - 1) ushr 5)
    }

    public fun write(words: IntArray, slotIndex: Int, slot: OfferSlot) {
        require(slotIndex in 0 until SLOTS) { "Slot index out of range: $slotIndex" }
        require(words.size >= WORDS) { "A slot store needs $WORDS words, got ${words.size}" }
        require(slot.itemId in 0..MAX_ITEM_ID) { "Item id out of range: ${slot.itemId}" }
        require(slot.quantity >= 0 && slot.price >= 0 && slot.completedQuantity >= 0)
        require(slot.completedGold in 0..OfferSlot.MAX_TOTAL) {
            "Gold out of range: ${slot.completedGold}"
        }
        val cursor = BitCursor(words, slotIndex * SLOT_BITS)
        cursor.write(slot.state.code.toLong(), STATE_BITS)
        cursor.write(slot.type.code.toLong(), TYPE_BITS)
        cursor.write(slot.itemId.toLong(), ITEM_BITS)
        cursor.write(slot.quantity.toLong(), INT_BITS)
        cursor.write(slot.price.toLong(), INT_BITS)
        cursor.write(slot.completedQuantity.toLong(), INT_BITS)
        cursor.write(slot.completedGold, GOLD_BITS)
    }

    public fun read(words: IntArray, slotIndex: Int, tax: Long = 0): OfferSlot {
        require(slotIndex in 0 until SLOTS) { "Slot index out of range: $slotIndex" }
        require(words.size >= WORDS) { "A slot store needs $WORDS words, got ${words.size}" }
        val cursor = BitCursor(words, slotIndex * SLOT_BITS)
        val state = OfferState.fromCode(cursor.read(STATE_BITS).toInt())
        if (state == OfferState.EMPTY) {
            return OfferSlot.EMPTY
        }
        val type = OfferType.fromCode(cursor.read(TYPE_BITS).toInt())
        val item = cursor.read(ITEM_BITS).toInt()
        val quantity = cursor.read(INT_BITS).toInt()
        val price = cursor.read(INT_BITS).toInt()
        val completedQuantity = cursor.read(INT_BITS).toInt()
        val gold = cursor.read(GOLD_BITS)
        return OfferSlot(state, type, item, quantity, price, completedQuantity, gold, tax)
    }

    /** The fee is kept in a single signed int per slot, so very large totals saturate. */
    public fun clampTax(tax: Long): Int = tax.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private class BitCursor(private val words: IntArray, private var position: Int) {
        fun write(value: Long, bits: Int) {
            var remaining = bits
            var v = value
            while (remaining > 0) {
                val word = position ushr 5
                val offset = position and 31
                val take = minOf(remaining, 32 - offset)
                val mask = ((1L shl take) - 1) shl offset
                val chunk = (v and ((1L shl take) - 1)) shl offset
                val merged = (words[word].toLong() and 0xFFFFFFFFL and mask.inv()) or chunk
                words[word] = merged.toInt()
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
