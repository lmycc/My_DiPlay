package com.shilapi.xcertplay

// carlito | BydCarPlayCall and BydOutputSettings removed; only the no-opt-in path remains.
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayCallControlsOptInTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun newCallKeysCannotConsumeOrSendAnythingWithoutOptIn() {
        val controller = mock(CarPlayController::class.java)
        for (code in listOf(313, 314, 309)) {
            assertFalse(CarPlayCallKeys.onKey(app, code, true, controller))
            assertFalse(CarPlayCallKeys.onKey(app, code, false, controller))
        }
        verifyNoInteractions(controller)
    }
}
