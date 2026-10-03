plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "dev.lua.seatpreset"
  compileSdk = 34
  useLibrary("android.test.runner")
  useLibrary("android.test.base")
  defaultConfig {
    applicationId = "dev.lua.seatpreset"
    minSdk = 29
    targetSdk = 29
    versionCode = providers.gradleProperty("seatVersionCode").orNull?.toInt() ?: 10
    versionName = providers.gradleProperty("seatVersionName").orNull ?: "0.1.9"
    testInstrumentationRunner = "android.test.InstrumentationTestRunner"
  }
  buildFeatures { buildConfig = true }
  flavorDimensions += "vehicle"
  productFlavors {
    create("demo") {
      dimension = "vehicle"
      applicationIdSuffix = ".demo"
      buildConfigField("boolean", "DEMO", "true")
      resValue("string", "app_name", "Seat Presets Demo")
    }
    create("live") {
      dimension = "vehicle"
      buildConfigField("boolean", "DEMO", "false")
      resValue("string", "app_name", "Seat Presets")
    }
  }
  val signingStore = System.getenv("SEAT_SIGNING_STORE")
  if (!signingStore.isNullOrBlank()) {
    signingConfigs {
      create("publicRelease") {
        storeFile = file(signingStore)
        storePassword = System.getenv("SEAT_SIGNING_STORE_PASSWORD")
        keyAlias = System.getenv("SEAT_SIGNING_ALIAS")
        keyPassword = System.getenv("SEAT_SIGNING_KEY_PASSWORD")
      }
    }
    buildTypes.getByName("release").signingConfig = signingConfigs.getByName("publicRelease")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  packaging { resources { excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md", "META-INF/NOTICE.md", "/META-INF/{AL2.0,LGPL2.1}") } }
  lint { disable += "ExpiredTargetSdkVersion" }
}
dependencies { implementation("dev.mobile:dadb:1.2.10"); testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
