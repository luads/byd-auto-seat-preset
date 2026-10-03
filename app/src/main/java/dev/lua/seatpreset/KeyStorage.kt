package dev.lua.seatpreset

/** Never inspect locked credential storage or replace an existing/partial key after a read error. */
internal object KeyStorage {
  fun <T> load(unlocked: Boolean, exists: () -> Pair<Boolean, Boolean>, read: () -> T,
    generate: () -> Unit): T {
    check(unlocked) { "user locked; do not inspect or replace adb key" }
    val (privateExists, publicExists) = exists()
    if (privateExists || publicExists) {
      check(privateExists && publicExists) { "partial existing key pair; refusing replacement" }
    } else generate()
    return read()
  }
}
