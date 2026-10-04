package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.content.quest.area.ardougne.clocktower.ClockTowerQuest
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<ClockTowerQuest>()
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
    }
}
