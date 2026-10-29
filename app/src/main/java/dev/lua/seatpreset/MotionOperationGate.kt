package dev.lua.seatpreset

/** Serialize package replacement and native signal ownership without persisting either request. */
internal class MotionOperationGate {
  private var motion=false
  private var installation=false
  @Synchronized fun beginMotion():Boolean { if(motion || installation) return false;motion=true;return true }
  @Synchronized fun endMotion() { motion=false }
  @Synchronized fun beginInstall():Boolean { if(motion || installation) return false;installation=true;return true }
  @Synchronized fun endInstall() { installation=false }
  @Synchronized fun moving()=motion
}
