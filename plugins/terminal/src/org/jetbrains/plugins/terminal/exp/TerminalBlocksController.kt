// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class TerminalBlocksController(
  project: Project,
  private val session: TerminalSession,
  settings: JBTerminalSystemSettingsProviderBase
) : ComponentContainer, TerminalCommandExecutor, ShellCommandListener {
  private val blocksComponent: TerminalBlocksComponent

  init {
    blocksComponent = TerminalBlocksComponent(project, session, settings, commandExecutor = this, parentDisposable = this)
    blocksComponent.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        sizeTerminalToComponent()
      }
    })

    session.addCommandListener(this, parentDisposable = this)
    session.model.addTerminalListener(object : TerminalModel.TerminalListener {
      override fun onAlternateBufferChanged(enabled: Boolean) {
        invokeLater {
          blocksComponent.toggleFullScreen(enabled)
        }
      }
    })
  }

  override fun startCommandExecution(command: String) {
    blocksComponent.installRunningPanel()
    session.executeCommand(command)
  }

  override fun commandFinished(command: String, exitCode: Int, duration: Long) {
    blocksComponent.makeCurrentBlockReadOnly()

    // prepare terminal for the next command
    val model = session.model
    model.lock()
    try {
      model.clearAllExceptPrompt()
    }
    finally {
      model.unlock()
    }

    invokeLater {
      blocksComponent.resetPromptPanel()
    }
  }

  fun sizeTerminalToComponent() {
    val newSize = blocksComponent.getTerminalSize()
    val model = session.model
    if (newSize.columns != model.width || newSize.rows != model.height) {
      // TODO: is it needed?
      //myTypeAheadManager.onResize()
      session.postResize(newSize)
    }
  }

  fun getTerminalSize(): TermSize = blocksComponent.getTerminalSize()

  fun isFocused(): Boolean {
    return blocksComponent.isFocused()
  }

  override fun dispose() {
  }

  override fun getComponent(): JComponent = blocksComponent

  override fun getPreferredFocusableComponent(): JComponent = blocksComponent.getPreferredFocusableComponent()
}