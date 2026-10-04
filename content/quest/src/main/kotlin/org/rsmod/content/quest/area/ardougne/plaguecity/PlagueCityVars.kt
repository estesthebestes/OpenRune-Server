package org.rsmod.content.quest.area.ardougne.plaguecity

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/** Edmond has followed the player down the hole; his garden self is hidden. */
internal var Player.edmondBelow by boolVarBit("varbit.plaguecity_can_see_edmond_up_top")

/** Elena is back in her own house; her `npc.elena2` form is shown. */
internal var Player.elenaHome by boolVarBit("varbit.plaguecity_elena_at_home")

/** The mud patch behind Edmond's house shows the dug hole instead of the mud. */
internal var Player.mudDug by boolVarBit("varbit.plaguecity_dug_mud_pile")

/** The player has tried to pull the grill off the sewer pipe by hand. */
internal var Player.grillChecked by boolVarBit("varbit.plaguecity_checked_grill")

/** [PIPE_BLOCKED], [PIPE_ROPE_TIED] or [PIPE_OPEN]; the grill and rope locs follow it. */
internal var Player.pipeState by intVarBit("varbit.plaguecity_pipe")

/** Jethick asked what Elena looks like, so the picture is worth fetching. */
internal var Player.pictureAsked by boolVarBit("varbit.plaguecity_picture_asked")

/** Elena told the player the cell key is stashed somewhere in the plague house. */
internal var Player.keyAsked by boolVarBit("varbit.plaguecity_key_asked")

/** Buckets of water poured on the mud patch so far. */
internal var Player.bucketsPoured by intVarBit("varbit.plaguecity_buckets_poured")

/** Edmond has explained the digging plan, so later chats just ask how it is going. */
internal var Player.toldToDig by boolVarBit("varbit.plaguecity_told_to_dig")

internal var Player.metJethick by boolVarBit("varbit.plaguecity_met_jethick")

internal var Player.gotNote by boolVarBit("varbit.plaguecity_got_note")

/** The first teleport scroll has been read; later ones burn to ashes. */
internal var Player.readScroll by boolVarBit("varbit.plaguecity_read_scroll")

internal const val PIPE_BLOCKED = 0
internal const val PIPE_ROPE_TIED = 1
internal const val PIPE_OPEN = 2
