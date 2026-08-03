package com.tanmay.notificationmirror.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyActionsTest {

    private fun summary(
        index: Int,
        isReplySemantic: Boolean = false,
        hasActionIntent: Boolean = true,
        freeFormResultKeys: List<String> = listOf("key_$index")
    ) = ActionSummary(index, isReplySemantic, hasActionIntent, freeFormResultKeys)

    @Test
    fun `prefers the action Android labels as a reply`() {
        val actions = listOf(
            summary(0),                          // "Mark as read" with an input attached
            summary(1, isReplySemantic = true)
        )

        assertEquals(1, selectReplyAction(actions)?.index)
    }

    @Test
    fun `falls back to the first usable action when nothing is labelled`() {
        val actions = listOf(summary(0), summary(1))

        assertEquals(0, selectReplyAction(actions)?.index)
    }

    @Test
    fun `ignores an action with no text input`() {
        val actions = listOf(summary(0, freeFormResultKeys = emptyList()), summary(1))

        assertEquals(1, selectReplyAction(actions)?.index)
    }

    @Test
    fun `ignores a labelled reply action that carries no pending intent`() {
        val actions = listOf(
            summary(0),
            summary(1, isReplySemantic = true, hasActionIntent = false)
        )

        assertEquals(0, selectReplyAction(actions)?.index)
    }

    @Test
    fun `returns null when no action qualifies`() {
        val actions = listOf(summary(0, freeFormResultKeys = emptyList()))

        assertNull(selectReplyAction(actions))
    }

    @Test
    fun `returns null for a notification with no actions at all`() {
        assertNull(selectReplyAction(emptyList()))
    }
}
