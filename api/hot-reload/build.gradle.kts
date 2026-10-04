plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.inline.logger)
    runtimeOnly(libs.logback.classic)
    implementation(libs.guice)
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.api.gameProcess)
    implementation(projects.api.playerOutput)
    implementation(projects.api.script)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.module)
    implementation(projects.engine.plugin)
}
