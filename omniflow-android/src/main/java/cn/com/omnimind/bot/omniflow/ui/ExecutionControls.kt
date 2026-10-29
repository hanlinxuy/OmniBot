package cn.com.omnimind.bot.omniflow.ui

import android.content.Context
import cn.com.omnimind.androidgui.AndroidGuiAccessibilityStatus
import cn.com.omnimind.androidgui.AndroidGuiEnvironment
import cn.com.omnimind.baselib.runlog.Action
import cn.com.omnimind.baselib.runlog.OobActionSchema
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class ExecutionControls private constructor(
    private val controls: ExecutionOverlay.Session?,
    private val guiEnvironment: AndroidGuiEnvironment,
    private val stopRequested: AtomicBoolean,
    private val completionRequested: AtomicBoolean,
    private val dispatchStop: () -> Unit,
) {
    suspend fun awaitRunning() {
        controls?.awaitRunning()
        if (completionRequested.get()) throw ManualCompletionRequested()
        if (stopRequested.get()) throw CancellationException("GUI execution stopped")
    }

    fun requestStop() {
        controls?.requestStop() ?: dispatchStop()
    }

    fun update(message: String) {
        controls?.update(message)
    }

    fun updatePhase(phase: ExecutionPhase) {
        controls?.updatePhase(phase)
    }

    suspend fun avoidAction(action: Action) {
        val activeControls = controls ?: return
        val display = guiEnvironment.displaySize()
        val feedback = executionActionFeedback(
            action = action,
            displayWidth = display.first,
            displayHeight = display.second,
        )
        val target = feedback ?: if (action.tool == OobActionSchema.TOOL_INPUT_TEXT) {
            executionActionFeedback(action.copy(tool = OobActionSchema.TOOL_CLICK), display.first, display.second)
        } else null
        if (target != null) activeControls.avoidTarget(target)
        if (feedback == null) return
        withContext(Dispatchers.Main.immediate) {
            activeControls.showActionFeedback(feedback)
        }
    }

    suspend fun hideForScreenshot() {
        controls?.hideForScreenshot()
    }

    suspend fun showAfterScreenshot() {
        controls?.showAfterScreenshot()
    }

    suspend fun finish(message: String, visibleMs: Long = 900L) {
        withContext(NonCancellable) {
            controls?.finish(message, visibleMs)
        }
    }

    companion object {
        suspend fun start(
            context: Context,
            title: String,
            initialPhase: ExecutionPhase,
            onStop: () -> Unit,
            onComplete: () -> Unit,
        ): ExecutionControls {
            val stopRequested = AtomicBoolean(false)
            val completionRequested = AtomicBoolean(false)
            val dispatchStop = {
                if (stopRequested.compareAndSet(false, true)) onStop()
            }
            val dispatchComplete = {
                if (completionRequested.compareAndSet(false, true)) onComplete()
            }
            val guiEnvironment = AndroidGuiEnvironment(context)
            if (
                guiEnvironment.accessibilityStatus() ==
                AndroidGuiAccessibilityStatus.CONNECTING
            ) {
                guiEnvironment.awaitReady()
            }
            val controls = withContext(Dispatchers.Main) {
                ExecutionOverlay.show(
                    context = context,
                    goal = title,
                    initialPhase = initialPhase,
                    onComplete = dispatchComplete,
                    onStop = dispatchStop,
                )
            }
            return ExecutionControls(
                controls,
                guiEnvironment,
                stopRequested,
                completionRequested,
                dispatchStop,
            )
        }
    }
}

internal class ManualCompletionRequested : CancellationException("GUI task completed by user")
