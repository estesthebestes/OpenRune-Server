package org.rsmod.api.realm.config

import org.rsmod.api.db.DatabaseConnection

public object RealmConfigWriter {
    private const val UPDATE_GLOBAL_XP =
        "UPDATE realms SET global_xp_rate_in_hundreds = ? WHERE realm_id = ?"

    private const val UPDATE_BASE_XP =
        "UPDATE realms SET player_xp_rate_in_hundreds = ? WHERE realm_id = ?"

    public fun updateGlobalXpRate(
        connection: DatabaseConnection,
        realmId: Int,
        rateInHundreds: Int,
    ): Int = update(connection, UPDATE_GLOBAL_XP, realmId, rateInHundreds)

    public fun updateBaseXpRate(
        connection: DatabaseConnection,
        realmId: Int,
        rateInHundreds: Int,
    ): Int = update(connection, UPDATE_BASE_XP, realmId, rateInHundreds)

    private fun update(connection: DatabaseConnection, sql: String, realmId: Int, value: Int): Int =
        connection.prepareStatement(sql).use {
            it.setInt(1, value)
            it.setInt(2, realmId)
            it.executeUpdate()
        }
}
