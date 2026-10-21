plugins { id("com.android.application") }

android {
    namespace = "com.hammad.autohzmax"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hammad.autohzmax"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    signingConfigs {
        create("shared") {
            storeFile = file("autohzmax.jks")
            storePassword = "autohzmax"
            keyAlias = "autohzmax"
            keyPassword = "autohzmax"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("shared") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
