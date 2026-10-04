package org.rsmod.api.hotreload

import org.rsmod.game.entity.player.PlayerUid

public sealed interface ReloadRequester {
    public data class Admin(val uid: PlayerUid, val name: String) : ReloadRequester

    public data object Watcher : ReloadRequester

    public data object Server : ReloadRequester
}
