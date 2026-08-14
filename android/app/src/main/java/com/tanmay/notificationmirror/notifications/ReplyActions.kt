package com.tanmay.notificationmirror.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle

/** The part of a Notification.Action this feature reasons about. */
internal data class ActionSummary(
    val index: Int,
    val isReplySemantic: Boolean,
    val hasActionIntent: Boolean,
    val freeFormResultKeys: List<String>
)

/**
 * Picks the action that accepts a typed reply. Taking the first action that
 * happens to carry a remote input — the common shortcut — can land on
 * "Mark as read", so the one Android labels SEMANTIC_ACTION_REPLY wins.
 */
internal fun selectReplyAction(actions: List<ActionSummary>): ActionSummary? {
    val usable = actions.filter { it.hasActionIntent && it.freeFormResultKeys.isNotEmpty() }
    return usable.firstOrNull { it.isReplySemantic } ?: usable.firstOrNull()
}

/** A live reply handle. Never leaves the device: a PendingIntent cannot be serialized. */
internal class ReplyTarget(
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
) {
    /** Builds the intent the posting app reads its reply out of. */
    fun fillIn(message: String): Intent {
        val results = Bundle()
        remoteInputs.forEach { results.putCharSequence(it.resultKey, message) }
        return Intent().also { RemoteInput.addResultsToIntent(remoteInputs, it, results) }
    }
}

internal fun Notification.replyTarget(): ReplyTarget? {
    val actions = actions?.toList().orEmpty()
    val chosen = selectReplyAction(actions.mapIndexed { index, action -> action.summarize(index) })
        ?: return null
    val action = actions[chosen.index]
    val inputs = action.remoteInputs
        ?.filter { it.resultKey in chosen.freeFormResultKeys }
        ?.toTypedArray()
        ?: return null
    return ReplyTarget(action.actionIntent ?: return null, inputs)
}

private fun Notification.Action.summarize(index: Int): ActionSummary {
    // The builder already keeps data-only inputs out of getRemoteInputs(), but
    // subtracting them costs nothing and the two lists are easy to confuse.
    val dataOnly = dataOnlyRemoteInputs?.map { it.resultKey }?.toSet().orEmpty()
    val keys = remoteInputs.orEmpty()
        .filter { it.allowFreeFormInput && it.resultKey !in dataOnly }
        .map { it.resultKey }
    return ActionSummary(
        index = index,
        isReplySemantic = semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY,
        hasActionIntent = actionIntent != null,
        freeFormResultKeys = keys
    )
}
