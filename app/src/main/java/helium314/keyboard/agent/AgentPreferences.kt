// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

object AgentPreferences {
    /** Opt-in wheel on space hold from before the per-gesture settings; read only while [SpaceGesture.HOLD] has no own value. */
    const val SPACE_LONG_PRESS = "agent_space_long_press"
    const val OPEN_AI_SETTINGS = "agent_open_ai_settings"
    /** Intent extra: one of [SETTINGS_DESTINATIONS], opened instead of the settings start page. */
    const val OPEN_SETTINGS_DESTINATION = "agent_open_settings_destination"
    /** The commands page (SettingsDestination.AgentCommands), opened by commands to ask for permissions or services. */
    const val COMMANDS_DESTINATION = "agent_commands"
    /** The Updates page (SettingsDestination.AgentUpdates), opened by the update notification. */
    const val UPDATES_DESTINATION = "agent_updates"
    val SETTINGS_DESTINATIONS = setOf("appearance", "toolbar", "agent_design", COMMANDS_DESTINATION,
        "preferences", "agent_workflows", // stage 24f: clipboard history setting, workflows
        UPDATES_DESTINATION,
        "languages", // stage 24g: @lang with only one enabled language
        "agent_symbols", // stage 27: "Nicht erkannt" opens the own drawn symbols
        MY_DATA_DESTINATION) // stage 45: "Meine Daten" from the vault
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
    /** Opt-in on top of [ADAPT_TOUCH]: short-term layer and scene profiles of the hit zones (stage 50). */
    const val ADAPT_TOUCH_DYNAMIC = "agent_adapt_touch_dynamic"
    const val SCREENSHOT_CHIP = "agent_screenshot_chip"
    /** Stage 41: hint chips for e-mail, phone, IBAN and decimal fields (default on). */
    const val FORMAT_GUARD = "agent_format_guard"
    /** Stage 45: field and context suggestions in the strip (default on; "Meine Daten" starts empty). */
    const val FIELD_SUGGESTIONS = "agent_field_suggestions"
    /** The page "Meine Daten & Vorschläge" (SettingsDestination.AgentMyData), opened from the vault. */
    const val MY_DATA_DESTINATION = "agent_my_data"
    /** Stage 46: the memory learns phrases, own words and abbreviations from typed words (opt-in, default off). */
    const val MEMORY = "agent_memory"
    /** Stage 46: a fitting snippet as a chip while typing (default on; learns nothing, works without [MEMORY]). */
    const val MEMORY_SNIPPETS = "agent_memory_snippets"
    /** Typed commands like "@math" (stage 24, default on). */
    const val COMMANDS = "agent_commands"
    /** One character from CommandSyntax.ALLOWED_PREFIXES. */
    const val COMMAND_PREFIX = "agent_command_prefix"
    /** Names of switched-off commands. */
    const val COMMANDS_DISABLED = "agent_commands_disabled"
    /** Last output format of the calculator, a MathExpression.Style name. */
    const val CALCULATOR_STYLE = "agent_calculator_style"
    /** Stage 27: drawn symbols instead of gesture typing (default off). */
    const val SYMBOL_DRAWING = "agent_symbol_drawing"
    /** Stage 36: a picked alternative keeps the drawing as a personal template (learning, default off). */
    const val SYMBOL_LEARNING = "agent_symbol_learning"
    /** privateImeOptions of the calibration field in the settings: the keyboard records touches there. */
    const val TOUCH_CALIBRATION_FIELD = "dev.personal.agentkeyboard.touchCalibration"
}
