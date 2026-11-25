plugins {
    alias(libs.plugins.app.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kokouliss.closetanything.feature.household"
}

dependencies {
    // Camera and gallery launchers for photos.
    implementation(libs.androidx.activity.compose)
    implementation(libs.google.play.services.code.scanner)
    implementation(libs.kotlinx.serialization)
    implementation(libs.zxing.core)

    testImplementation(libs.bundles.unit.test)
}
