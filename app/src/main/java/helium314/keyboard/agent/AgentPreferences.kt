// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

object AgentPreferences {
    const val SPACE_LONG_PRESS = "agent_space_long_press"
    const val OPEN_AI_SETTINGS = "agent_open_ai_settings"
    /** Keeps what was learned but adds nothing new. Different from switching learning off, which deletes. */
    const val LEARNING_PAUSED = "agent_learning_paused"
}
