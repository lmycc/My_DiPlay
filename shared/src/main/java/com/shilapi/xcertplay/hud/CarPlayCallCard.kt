// carlito | Minimal call card model. Replaces the BYD-specific implementation
// that was in BydCarPlayCall.kt. Preserves the interface used by CarPlayCallKeyPolicy.
package com.shilapi.xcertplay.hud

/**
 * The current CarPlay call state, as seen by the steering wheel call-key policy.
 * [phase] tracks whether a call is ringing, active, or ended; [name] is the caller name.
 */
data class CarPlayCallCard(
    val phase: Phase,
    val name: String?,
) {
    enum class Phase { IDLE, RINGING, ACTIVE, ENDED }
}
