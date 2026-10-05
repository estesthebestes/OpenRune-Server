package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.fightarena.ArenaInstance
import org.rsmod.content.quest.area.ardougne.fightarena.ArenaSite
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaDeathHook
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaDoors
import org.rsmod.content.quest.area.ardougne.fightarena.FightArenaScenes
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
        bindInstance<QuestDoors>()
        bindInstance<EdmondsGarden>()
        bindInstance<ArenaInstance>()
        bind(ArenaSite::class.java).to(ArenaInstance::class.java)
        bindInstance<FightArenaDoors>()
        bindInstance<FightArenaScenes>()
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
        addSetBinding<SpadeDigHook>(XMarksTheSpot::class.java)
        addSetBinding<SpadeDigHook>(EdmondsGarden::class.java)
        addSetBinding<PlayerDeathHook>(FightArenaDeathHook::class.java)
    }
}
