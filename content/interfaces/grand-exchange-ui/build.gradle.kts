plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.rsprot.api)
    implementation(libs.fastutil)
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.grandExchange)
}
