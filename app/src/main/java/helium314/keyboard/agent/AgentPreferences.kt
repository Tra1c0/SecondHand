// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

object AgentPreferences {
    const val SPACE_LONG_PRESS = "agent_space_long_press"
    const val OPEN_AI_SETTINGS = "agent_open_ai_settings"
    /** Intent extra: one of [SETTINGS_DESTINATIONS], opened instead of the settings start page. */
    const val OPEN_SETTINGS_DESTINATION = "agent_open_settings_destination"
    /** The commands page (SettingsDestination.AgentCommands), opened by commands to ask for permissions or services. */
    const val COMMANDS_DESTINATION = "agent_commands"
    val SETTINGS_DESTINATIONS = setOf("appearance", "toolbar", "agent_design", COMMANDS_DESTINATION,
        "preferences", "agent_workflows") // stage 24f: clipboard history setting, workflows
    /** Keeps what was learned but adds nothing new. Different from switching learning off, which deletes. */
    const val LEARNING_PAUSED = "agent_learning_paused"
    /** Never replace a known word automatically (default off: known words may still be corrected when clearly wrong). */
    const val AUTOCORRECT_KEEP_VALID_WORDS = "agent_autocorrect_keep_valid_words"
    /** Never replace a known word by one that differs only by an umlaut, e.g. mochte/möchte (default on). */
    const val AUTOCORRECT_KEEP_UMLAUT_VARIANTS = "agent_autocorrect_keep_umlaut_variants"
    /** Opt-in: remember corrections undone with backspace and never apply them again. */
    const val AUTOCORRECT_REMEMBER_REJECTED = "agent_autocorrect_remember_rejected"
    /** Opt-in: learn own typos from words picked in the suggestion strip (stage 20b). */
    const val AUTOCORRECT_LEARN_TYPOS = "agent_autocorrect_learn_typos"
    /** Opt-in: personal hit boxes for letter keys (stage 22). */
    const val ADAPT_TOUCH = "agent_adapt_touch"
    const val SCREENSHOT_CHIP = "agent_screenshot_chip"
    /** Typed commands like "@math" (stage 24, default on). */
    const val COMMANDS = "agent_commands"
    /** One character from CommandSyntax.ALLOWED_PREFIXES. */
    const val COMMAND_PREFIX = "agent_command_prefix"
    /** Names of switched-off commands. */
    const val COMMANDS_DISABLED = "agent_commands_disabled"
    /** Last output format of the calculator, a MathExpression.Style name. */
    const val CALCULATOR_STYLE = "agent_calculator_style"
    /** privateImeOptions of the calibration field in the settings: the keyboard records touches there. */
    const val TOUCH_CALIBRATION_FIELD = "dev.personal.agentkeyboard.touchCalibration"
}
