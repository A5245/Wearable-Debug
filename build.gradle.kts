plugins {
    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
}

tasks.register<Delete>("clean").configure {
    delete(layout.buildDirectory)
}
