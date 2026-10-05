package org.rsmod.content.quest.area.wilderness.entertheabyss

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

var Player.etaHeardOffer by boolVarBit("varbit.abyssal_miniquest_intro")
var Player.etaReconsidering by boolVarBit("varbit.abyssal_miniquest_reconsider")
var Player.etaOrbGiven by boolVarBit("varbit.abyssal_miniquest_orb")
var Player.etaRewarded by boolVarBit("varbit.abyssal_miniquest_reward")
