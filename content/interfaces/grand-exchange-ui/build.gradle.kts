plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.rsprot.api)
    testImplementation(libs.or2.all.cache)
    testImplementation(libs.or2.definition)
    testImplementation(libs.or2.filesystem)
    testImplementation(projects.content.generic.genericLocs)
    implementation(libs.fastutil)
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.grandExchange)
}
