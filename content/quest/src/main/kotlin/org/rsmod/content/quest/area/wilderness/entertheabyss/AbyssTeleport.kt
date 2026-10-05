package org.rsmod.content.quest.area.wilderness.entertheabyss

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.stat.statSub
import org.rsmod.content.areas.wilderness.applyAbyssSkull
import org.rsmod.content.areas.wilderness.hasActiveSkull
import org.rsmod.content.skills.runecrafting.essence.castTeleport
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType

/**
 * The Mage of Zamorak's teleport into the Abyss's outer ring. Each trip rolls one of the twelve
 * obstacle layouts and lands the player in front of that layout's blockage. Arrival drains prayer to zero (the
 * prayer drain timer then switches active prayers off as usual) and skulls the player, unless a
 * worn abyssal bracelet spends a charge instead; an already-skulled player keeps their skull and
 * their bracelet charges.
 */
@Singleton
class AbyssTeleport @Inject constructor() {

    suspend fun teleport(access: ProtectedAccess, npc: Npc) {
        access.castTeleport(npc, INCANTATION)
        val layout = access.random.of(AbyssGate.LAYOUTS)
        val dest = AbyssGate.entries[layout].front
        access.telejump(dest)
        if (access.player.coords != dest) {
            return
        }
        access.vars[AbyssGate.LAYOUT_VARBIT] = layout
        arrive(access)
    }

    internal fun arrive(access: ProtectedAccess) {
        val player = access.player
        val prayer = player.prayerLvl
        if (prayer > 0) {
            player.statSub(PRAYER, constant = prayer, percent = 0)
        }
        if (access.useBraceletCharge()) {
            return
        }
        player.applyAbyssSkull()
    }

    private fun ProtectedAccess.useBraceletCharge(): Boolean {
        val slot = Wearpos.Hands.slot
        val worn = worn[slot]
        val charges = BRACELETS.indexOfFirst { worn.isType(it) }
        if (charges < 0) {
            return false
        }
        if (player.hasActiveSkull()) {
            return true
        }
        if (charges == BRACELETS.lastIndex) {
            invDel(this.worn, BRACELETS[charges], count = 1, slot = slot)
            mes("Your abyssal bracelet crumbles to dust.")
            return true
        }
        val next = ServerCacheManager.getItem(BRACELETS[charges + 1].asRSCM(RSCMType.OBJ))
        if (next != null) {
            invReplaceSlot(this.worn, slot, 1, next)
        }
        return true
    }

    companion object {
        const val INCANTATION = "Veniens! Sallakar! Rinnesset!"
        private const val PRAYER = "stat.prayer"

        /** Abyssal bracelet (5) down to (1). */
        val BRACELETS =
            listOf(
                "obj.jewl_runerunning_bracelet_5",
                "obj.jewl_runerunning_bracelet_4",
                "obj.jewl_runerunning_bracelet_3",
                "obj.jewl_runerunning_bracelet_2",
                "obj.jewl_runerunning_bracelet_1",
            )
    }
}
