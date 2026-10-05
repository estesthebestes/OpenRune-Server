plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.api.shops)
    implementation(projects.api.areaChecker)
    implementation(projects.api.route)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.serverConfig)
    implementation(libs.rsprot.api)
}
