package org.rsmod.content.quest.area.kourend

enum class KourendStore(
    val keeper: String,
    val city: String,
    internal val talkedVarbit: String,
) {
    Piscarilius("Leenz", "Port Piscarilius", "varbit.veos_piscarilius"),
    Arceuus("Regath", "Arceuus", "varbit.veos_arceuus"),
    Lovakengj("Munty", "Lovakengj", "varbit.veos_lovakengj"),
    Shayzien("Jennifer", "Shayzien", "varbit.veos_shayzien"),
    Hosidius("Horace", "Hosidius", "varbit.veos_hosidius"),
}
