package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest
import org.rsmod.content.quest.area.ardougne.biohazard.GuidorsHouse
import org.rsmod.content.quest.area.ardougne.biohazard.Smuggling
import org.rsmod.content.quest.area.ardougne.plaguecity.EdmondsGarden
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.XMarksTheSpot
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<XMarksTheSpot>()
        bindInstance<PlagueCityQuest>()
        bindInstance<BiohazardQuest>()
        bindInstance<Smuggling>()
        bindInstance<GuidorsHouse>()
        bindInstance<QuestDoors>()
        bindInstance<EdmondsGarden>()
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
        addSetBinding<SpadeDigHook>(XMarksTheSpot::class.java)
        addSetBinding<SpadeDigHook>(EdmondsGarden::class.java)
    }
}
