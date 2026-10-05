package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Shattered cannon ornament kit. One kit goes on each of the four cannon parts, turning it
 * into its Shattered Relics version; Dismantle takes the kit back off again.
 */
class CannonOrnamentKit : PluginScript() {

    override fun ScriptContext.startup() {
        for (index in PART_NAMES.indices) {
            val plain = CannonStyle.Normal.parts[index]
            val ornate = CannonStyle.Ornate.parts[index]
            onOpHeldU(KIT, plain) { attach(plain, ornate, PART_NAMES[index]) }
            onOpHeld4(ornate) { dismantle(ornate, plain, PART_NAMES[index]) }
        }
    }

    private suspend fun ProtectedAccess.attach(plain: String, ornate: String, name: String) {
        var confirmed = false
        startDialogue {
            confirmed =
                choice2("Yes", true, "No", false, title = "Apply Shattered Relics League appearance to the $name?")
        }
        if (!confirmed || KIT !in inv || plain !in inv) {
            return
        }
        if (invDel(inv, KIT).failure || invReplace(inv, plain, 1, ornate).failure) {
            return
        }
        startDialogue { objbox(ornate, "You apply the Shattered Relics League appearance to the $name.") }
    }

    private fun ProtectedAccess.dismantle(ornate: String, plain: String, name: String) {
        if (inv.freeSpace() < 1) {
            mes("You need a free inventory space to dismantle the $name.")
            return
        }
        if (invReplace(inv, ornate, 1, plain).failure) {
            return
        }
        invAdd(inv, KIT)
        mes("You remove the ornament kit from the $name.")
    }

    private companion object {
        const val KIT = "obj.league_3_multicannon_pack"

        val PART_NAMES = listOf("Cannon base", "Cannon stand", "Cannon barrels", "Cannon furnace")
    }
}
