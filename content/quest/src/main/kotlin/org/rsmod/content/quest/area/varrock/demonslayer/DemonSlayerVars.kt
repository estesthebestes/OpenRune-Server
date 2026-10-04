package org.rsmod.content.quest.area.varrock.demonslayer

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/* Jagex's own varbits, all packed into varp.demonstart next to the quest progress varbit. */

var Player.incantation1: Int by intVarBit("varbit.delrith_incantation_1")
var Player.incantation2: Int by intVarBit("varbit.delrith_incantation_2")
var Player.incantation3: Int by intVarBit("varbit.delrith_incantation_3")
var Player.incantation4: Int by intVarBit("varbit.delrith_incantation_4")
var Player.incantation5: Int by intVarBit("varbit.delrith_incantation_5")

/** 0: key in the drain, 1: washed into the sewer, 2: picked up. Drives the drain multilocs. */
var Player.drainKeyState: Int by intVarBit("varbit.delrith_drain_key")

/** Set once Silverlight has been handed over; swaps the sword case in Sir Prysin's room. */
var Player.silverlightCaseEmpty: Boolean by boolVarBit("varbit.delrith_silverlight_case")

/** Set once the summoning has been seen; swaps the stone table at the circle for the broken one. */
var Player.seenSummoning: Boolean by boolVarBit("varbit.delrith_seen_summoning_cutscene")

/* Server-only state in varp.demon_slayer_state. */

var Player.traibornBonesGiven: Int by intVarBit("varbit.demon_slayer_bones_given")
var Player.traibornAsked: Boolean by boolVarBit("varbit.demon_slayer_traiborn_asked")
var Player.traibornKeyGiven: Boolean by boolVarBit("varbit.demon_slayer_traiborn_key_given")
