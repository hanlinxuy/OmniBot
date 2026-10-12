package cn.com.omnimind.bot.ui.chat.browser

import androidx.lifecycle.SavedStateHandle
import cn.com.omnimind.bot.agent.LiveAgentBrowserSessionManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBrowserViewModelTest {
    private fun session(workspaceId: String) = mapOf(
        "available" to true, "workspaceId" to workspaceId, "currentUrl" to "https://a.io",
        "tabs" to listOf(mapOf("tabId" to 1, "url" to "https://a.io", "isActive" to true)),
    )

    @Test fun followsOnlyItsOwnConversation() {
        val viewModel = NativeBrowserViewModel(SavedStateHandle())
        viewModel.follow(7)
        // Dart followed whichever engine ran last; another conversation's page must not show here.
        LiveAgentBrowserSessionManager.publish("conversation_8", session("conversation_8"))
        assertNull(viewModel.state.value.session)
        LiveAgentBrowserSessionManager.publish("conversation_7", session("conversation_7"))
        assertNotNull(viewModel.state.value.session)
    }

    @Test fun opensOnlyWithASessionAndClosesWhenItEnds() {
        val viewModel = NativeBrowserViewModel(SavedStateHandle())
        viewModel.follow(9)
        viewModel.toggle()
        assertFalse(viewModel.state.value.open)
        LiveAgentBrowserSessionManager.publish("conversation_9", session("conversation_9"))
        viewModel.toggle()
        assertTrue(viewModel.state.value.open)
        // The agent closed its last tab: nothing left to show.
        LiveAgentBrowserSessionManager.publish("conversation_9", mapOf("available" to false, "workspaceId" to "conversation_9"))
        assertFalse(viewModel.state.value.open)
        assertNull(viewModel.state.value.session)
    }
}
