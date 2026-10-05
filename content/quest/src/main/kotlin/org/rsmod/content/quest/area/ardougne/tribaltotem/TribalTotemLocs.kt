package org.rsmod.content.quest.area.ardougne.tribaltotem

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.AddressLabel
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Delivered
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.LabelPlaced
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.SewerLanding
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.StairsTop
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Started
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.Totem
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.TrapFound
import org.rsmod.content.quest.area.ardougne.tribaltotem.TribalTotemQuest.Companion.TrapThievingLevel
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The scenery of the quest: the two crates at the GPDT depot, the mansion's locked front door,
 * the trapped stairs and the chest with the totem. The combination door lives in
 * [HandelmortDoor].
 */
class TribalTotemLocs
@Inject
constructor(
    private val tribalTotem: TribalTotemQuest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val quest
        get() = tribalTotem.quest

    override fun ScriptContext.startup() {
        onOpLoc2(LabelCrate) { investigateLabelCrate() }
        onOpLoc2(BlockCrate) { investigateBlockCrate() }
        onOpLocU(BlockCrate, AddressLabel) { replaceLabel() }
        onOpLocU(BlockCrate) { mes("Nothing interesting happens.") }

        onOpLoc1(FrontDoor) { mes("This door is securely locked.") }

        onOpLoc1(Stairs) { climbStairs() }
        onOpLoc2(Stairs) { investigateStairs() }

        onOpLoc1(ClosedChest) { openChest(it.loc) }
        onOpLoc1(OpenChest) { searchChest() }
        onOpLoc2(OpenChest) { closeChest(it.loc) }
    }

    private suspend fun ProtectedAccess.investigateLabelCrate() {
        arriveDelay()
        val stage = tribalTotem.stage(player)
        val hasLabel = inv.contains(AddressLabel)
        when {
            stage == Started && !hasLabel -> {
                invAddOrDrop(objRepo, AddressLabel)
                mesbox(
                    "There is a label on this crate. It says: $LabelText You carefully peel it " +
                        "off and take it."
                )
            }
            stage == 0 -> mesbox("There is a label on this crate. It says: $LabelText")
            else ->
                mesbox("You can see the gluey outline from where you peeled the address label off.")
        }
    }

    private suspend fun ProtectedAccess.investigateBlockCrate() {
        arriveDelay()
        mesbox("There is a label on this crate. It says;")
        if (tribalTotem.stage(player) < LabelPlaced) {
            mesbox(
                "Senior Patents Clerk, Chamber of Invention, The Wizards' Tower, Misthalin. " +
                    "The crate is securely fastened shut and ready for delivery."
            )
        } else {
            mesbox("To Lord Handelmort, Handelmort Mansion, Ardougne.")
        }
    }

    private suspend fun ProtectedAccess.replaceLabel() {
        arriveDelay()
        val stage = tribalTotem.stage(player)
        if (stage >= LabelPlaced) {
            mes("You have already replaced the delivery address label.")
            return
        }
        if (stage != Started || invDel(inv, AddressLabel).failure) {
            mes("Nothing interesting happens.")
            return
        }
        quest.setQuestStage(this, LabelPlaced)
        mesbox(
            "You carefully place the delivery address label over the existing label, covering " +
                "it completely."
        )
        startDialogue { chatPlayer(neutral, "Now I just need someone to deliver it for me.") }
    }

    private suspend fun ProtectedAccess.climbStairs() {
        arriveDelay()
        if (tribalTotem.stage(player) < TrapFound) {
            mes("As you climb the stairs you hear a click...")
            delay(2)
            mes("You have fallen through a trap!")
            telejump(SewerLanding)
            return
        }
        mes("You climb the stairs.")
        telejump(StairsTop)
    }

    private suspend fun ProtectedAccess.investigateStairs() {
        arriveDelay()
        if (stat("stat.thieving") < TrapThievingLevel) {
            mes("You don't find anything interesting.")
            return
        }
        if (tribalTotem.stage(player) == Delivered) {
            quest.setQuestStage(this, TrapFound)
        }
        mesbox(
            "Your trained senses as a thief enable you to see that there is a trap in these " +
                "stairs. You make a note of its location for future reference when using " +
                "these stairs."
        )
    }

    private suspend fun ProtectedAccess.openChest(chest: BoundLocInfo) {
        arriveDelay()
        locRepo.del(chest, ChestTicks)
        locRepo.add(chest.coords, OpenChest, ChestTicks, chest.angle, chest.shape)
        mes("You open the chest.")
    }

    private suspend fun ProtectedAccess.closeChest(chest: BoundLocInfo) {
        arriveDelay()
        locRepo.del(chest, ChestTicks)
        locRepo.add(chest.coords, ClosedChest, ChestTicks, chest.angle, chest.shape)
    }

    private suspend fun ProtectedAccess.searchChest() {
        arriveDelay()
        val inProgress = quest.isQuestInProgress(player)
        if (!inProgress || inv.contains(Totem)) {
            mes("The chest is empty.")
            return
        }
        invAddOrDrop(objRepo, Totem)
        mesbox("Inside the chest you find the tribal totem.")
    }

    private companion object {
        const val LabelCrate = "loc.horncrate"
        const val BlockCrate = "loc.teleportcrate"
        const val FrontDoor = "loc.tribaltotemdoor"
        const val Stairs = "loc.totemtrapstairs"
        const val ClosedChest = "loc.totemshutchest"
        const val OpenChest = "loc.totemopenchest"

        const val ChestTicks = 100
        const val LabelText = "<col=0000ff>Lord Handelmort, Handelmort Mansion, Ardougne</col>"
    }
}
