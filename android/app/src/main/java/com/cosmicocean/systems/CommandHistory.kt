package com.cosmicocean.systems

import java.util.Stack

interface Command {
    fun execute()
    fun undo()
    val description: String
}

class CommandHistory {
    private val undoStack = Stack<Command>()
    private val redoStack = Stack<Command>()

    // Callback for UI notifications
    var onUndoRedo: ((action: String, description: String) -> Unit)? = null

    fun execute(command: Command) {
        command.execute()
        undoStack.push(command)
        redoStack.clear()

        // Limit history size to 50
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
    }

    fun undo(): String? {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.pop()
            command.undo()
            redoStack.push(command)

            // Trigger callback
            onUndoRedo?.invoke("Undo", command.description)

            return command.description
        }
        return null
    }

    fun redo(): String? {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.pop()
            command.execute()
            undoStack.push(command)

            // Trigger callback
            onUndoRedo?.invoke("Redo", command.description)

            return command.description
        }
        return null
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun getUndoDescription(): String? = undoStack.lastOrNull()?.description
    fun getRedoDescription(): String? = redoStack.lastOrNull()?.description

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
