plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.inline.logger)
    implementation(libs.okhttp)
    runtimeOnly(libs.logback.classic)
    implementation(projects.api.market)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.game)
    implementation(projects.engine.module)
    implementation(projects.engine.plugin)
    implementation(projects.server.services)
}
