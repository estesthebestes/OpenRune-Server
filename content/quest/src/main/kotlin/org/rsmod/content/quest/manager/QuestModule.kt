package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.varrock.demonslayer.DemonSlayerQuest
import org.rsmod.content.quest.area.varrock.demonslayer.SilverlightAttackHook
import org.rsmod.content.quest.area.varrock.demonslayer.StoneCircle
import org.rsmod.content.quest.area.varrock.demonslayer.StoneCircleWallyVision
import org.rsmod.content.quest.area.varrock.demonslayer.WallyVision
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.TraibornRitual
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.WardrobeRitual
import org.rsmod.content.quest.area.varrock.demonslayer.npcs.WizardTraiborn
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<DemonSlayerQuest>()
        bindInstance<StoneCircle>()
        bind(WallyVision::class.java).to(StoneCircleWallyVision::class.java)
        bindInstance<WizardTraiborn>()
        bind(TraibornRitual::class.java).to(WardrobeRitual::class.java)
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
        addSetBinding<NpcAttackValidateHook>(SilverlightAttackHook::class.java)
    }
}
