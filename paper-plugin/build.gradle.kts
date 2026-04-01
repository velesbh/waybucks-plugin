plugins {
    java
    id("com.gradleup.shadow")
}

group = "space.blockway.waybucks"
version = "1.0.0"

dependencies {
    implementation(project(":shared"))

    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("waybucks-paper")
    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
