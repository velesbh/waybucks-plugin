plugins {
    java
    id("com.gradleup.shadow")
}

group = "space.blockway.waybucks"
version = "1.0.0"

configurations {
    annotationProcessor
}

dependencies {
    implementation(project(":shared"))

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    // SLF4J provided by Velocity — compileOnly only
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    // Javalin embedded REST API
    implementation("io.javalin:javalin:6.3.0")

    // Jackson for REST JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.2")

    // HikariCP connection pool
    implementation("com.zaxxer:HikariCP:5.1.0")

    // SQLite JDBC — NOT relocated (native lib uses hardcoded class names)
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // MySQL
    implementation("com.mysql:mysql-connector-j:9.1.0")

    // SnakeYAML for config
    implementation("org.yaml:snakeyaml:2.3")

    // Gson for plugin messaging
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("waybucks-velocity")

    relocate("io.javalin", "space.blockway.waybucks.shadow.javalin")
    relocate("org.eclipse.jetty", "space.blockway.waybucks.shadow.jetty")
    relocate("jakarta", "space.blockway.waybucks.shadow.jakarta")
    relocate("com.fasterxml.jackson", "space.blockway.waybucks.shadow.jackson")
    relocate("com.zaxxer.hikari", "space.blockway.waybucks.shadow.hikari")
    // org.sqlite/org.xerial NOT relocated — native lib uses Class.forName with hardcoded names
    relocate("com.mysql", "space.blockway.waybucks.shadow.mysql")
    relocate("org.yaml.snakeyaml", "space.blockway.waybucks.shadow.snakeyaml")
    relocate("com.google.gson", "space.blockway.waybucks.shadow.gson")

    exclude("org/slf4j/**")
    exclude("META-INF/services/org.slf4j.*")

    mergeServiceFiles()
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
