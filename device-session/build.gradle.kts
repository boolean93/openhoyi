plugins { kotlin("jvm"); `java-library` }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":protocol-core"))
    testImplementation("junit:junit:4.13.2")
}
tasks.register<JavaExec>("verify") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.openhoyi.session.SessionChecksKt")
}
tasks.named("test") { dependsOn("verify") }
tasks.check { dependsOn("verify") }
