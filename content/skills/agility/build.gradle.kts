plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.content.other.pets)
    implementation(projects.content.quest)
    implementation(projects.content.skills.utils)
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.invStorage)
    testImplementation(libs.fastutil)
}

tasks.test {
    inputs.dir(rootProject.file(".data/raw-cache/server/loc"))
}
