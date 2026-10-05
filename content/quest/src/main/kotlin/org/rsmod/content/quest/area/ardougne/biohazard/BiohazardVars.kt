package org.rsmod.content.quest.area.ardougne.biohazard

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/** The four chat flags up to `postQuestChat` are cache varbits on `varp.elenaquest_extra_bits`. */
internal var Player.metOmart by boolVarBit("varbit.biohazard_met_omart")

internal var Player.metJulie by boolVarBit("varbit.biohazard_met_julie")

internal var Player.freeClothes by boolVarBit("varbit.biohazard_free_clothes")

internal var Player.postQuestChat by boolVarBit("varbit.biohazard_postquest_chat")

internal var Player.metJerico by boolVarBit("varbit.biohazard_met_jerico")

internal var Player.birdFeedThrown by boolVarBit("varbit.biohazard_birdfeed_thrown")

internal var Player.dummyHits by intVarBit("varbit.biohazard_dummy_hits")

/** What a vial in an errand boy's pocket is: [VIAL_NONE] or one of the three vials. */
internal var Player.chancyVial by intVarBit("varbit.biohazard_chancy_vial")
internal var Player.daVinciVial by intVarBit("varbit.biohazard_davinci_vial")
internal var Player.hopsVial by intVarBit("varbit.biohazard_hops_vial")

internal const val VIAL_NONE = 0
internal const val VIAL_ETHENEA = 1
internal const val VIAL_LIQUID_HONEY = 2
internal const val VIAL_SULPHURIC_BROLINE = 3
