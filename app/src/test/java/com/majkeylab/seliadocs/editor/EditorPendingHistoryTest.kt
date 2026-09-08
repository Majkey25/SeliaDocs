package com.majkeylab.seliadocs.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorPendingHistoryTest {
    @Test
    fun closeWaitsForPendingHistoryBeforeAndDuringSave() {
        listOf(EditorAction.Undo, EditorAction.Redo).forEach { history ->
            listOf(false, true).forEach { saving ->
                val holder = EditorSessionHolder()
                holder.prepare("notebook")
                holder.requestAction(history)
                if (saving) holder.beginActionSave()
                val close = EditorAction.Close(EditorCloseIntent.BACK)
                holder.requestAction(close)
                holder.requestAction(EditorAction.AddText)
                assertEquals(history, holder.actionState.value.pending)
                if (!saving) holder.beginActionSave()
                holder.completeActionSave(holder.sessionEpoch, true)
                assertEquals(history, holder.takeReadyAction())
                assertEquals(close, holder.actionState.value.pending)
                assertEquals(false, holder.actionState.value.ready)
                holder.beginActionSave()
                holder.completeActionSave(holder.sessionEpoch, true)
                assertEquals(close, holder.takeReadyAction())
            }
        }
    }

    @Test
    fun failedSaveAndNewSessionDiscardDeferredClose() {
        listOf(false, true).forEach { resetSession ->
            val holder = EditorSessionHolder()
            holder.prepare("first")
            holder.requestAction(EditorAction.Undo)
            holder.beginActionSave()
            holder.requestAction(EditorAction.Close(EditorCloseIntent.SETTINGS))
            if (resetSession) holder.prepare("second")
            else holder.completeActionSave(holder.sessionEpoch, false)
            holder.requestAction(EditorAction.SelectTool(EditorTool.PENCIL))
            holder.beginActionSave()
            holder.completeActionSave(holder.sessionEpoch, true)
            assertEquals(EditorAction.SelectTool(EditorTool.PENCIL), holder.takeReadyAction())
            assertEquals(null, holder.actionState.value.pending)
        }
    }
}
