plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.or2.definition)
    testImplementation(libs.or2.filesystem)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.equipment)
}
