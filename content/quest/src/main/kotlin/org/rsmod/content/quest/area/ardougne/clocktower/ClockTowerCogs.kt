package org.rsmod.content.quest.area.ardougne.clocktower

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpObj3
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ClockTowerCogs
@Inject
constructor(private val clockTower: ClockTowerQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (cog in Cog.entries) {
            onOpObj3(objType(cog.obj)) { take(it.obj, cog) }
            onOpLocU(cog.brokenSpindle) {
                useOnSpindle(it.objType.internalName, cog, broken = true)
            }
            onOpLocU(cog.fittedSpindle) {
                useOnSpindle(it.objType.internalName, cog, broken = false)
            }
        }
    }

    private suspend fun ProtectedAccess.take(obj: Obj, cog: Cog) {
        if (clockTower.isComplete(player)) {
            mes(Constants.dm_default)
            return
        }
        if (Cog.entries.any { it.obj in inv }) {
            mes(CarryingCog)
            return
        }
        if (inv.isFull()) {
            mes(Constants.dm_take_invspace)
            return
        }
        val hot = cog == Cog.BLACK && !wearingColdGloves()
        val water = if (hot) Waters.entries.firstOrNull { it.key in inv } else null
        if (hot && water == null) {
            mesbox(CogTooHot)
            return
        }
        if (coords != obj.coords) {
            delay(1)
            anim(TakeSeq)
        }
        soundSynth(TakeSound)
        if (!objRepo.del(obj)) {
            mes(Constants.dm_take_taken)
            return
        }
        if (water != null) {
            invReplace(inv, water.key, 1, water.value)
        }
        invAdd(inv, cog.obj)
        if (water != null) {
            mesbox(CogCooled)
        }
    }

    private fun ProtectedAccess.wearingColdGloves(): Boolean = ColdGloves.any { it in player.worn }

    private suspend fun ProtectedAccess.useOnSpindle(obj: String, spindle: Cog, broken: Boolean) {
        val cog = Cog.ofObj(obj)
        if (cog == null) {
            mes(Constants.dm_default)
            return
        }
        arriveDelay()
        when {
            cog != spindle -> mes("The ${cog.label} cog doesn't fit on this spindle.")
            !broken -> mes("This spindle already has a cog on it.")
            !clockTower.isActive(player) -> mes(Constants.dm_default)
            clockTower.isPlaced(player, cog) -> mes("You've already fitted a cog to this spindle.")
            else -> fit(cog)
        }
    }

    private fun ProtectedAccess.fit(cog: Cog) {
        if (invDel(inv, cog.obj).failure) {
            return
        }
        clockTower.place(player, cog)
        anim(FitSeq)
        soundSynth(FitSound)
        mes("The cog fits perfectly.")
    }

    private fun objType(obj: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) { "Missing obj: $obj" }

    private companion object {
        const val TakeSeq = "seq.human_pickuptable"
        const val FitSeq = "seq.human_pickuptable"
        const val TakeSound = "synth.pick2"
        const val FitSound = "synth.pick2"

        const val CarryingCog = "The cogs are too heavy to carry more than one at a time."
        const val CogTooHot = "The cog is red hot from the flames. You cannot pick it up."
        const val CogCooled = "You pour water over the cog. It quickly cools down enough to take."

        val ColdGloves = listOf("obj.ice_gloves", "obj.smithing_uniform_gloves_ice")

        val Waters =
            linkedMapOf(
                "obj.bucket_water" to "obj.bucket_empty",
                "obj.jug_water" to "obj.jug_empty",
            )
    }
}
