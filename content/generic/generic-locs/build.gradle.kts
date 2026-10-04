plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    testImplementation(libs.fastutil)
}

tasks.test {
    inputs.dir(rootProject.file(".data/raw-cache/server/loc"))
}
