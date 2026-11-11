// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

enum class ProfileGroup { DESIGN, LAYOUT, GESTURES }

sealed interface PreferenceValue {
    data class Flag(val value: Boolean) : PreferenceValue
    data class Choice(val value: String) : PreferenceValue
    data class Scale(val value: Float) : PreferenceValue
}

data class ConfigurationProfile(
    val formatVersion: Int = 2,
    val group: ProfileGroup,
    val values: Map<String, PreferenceValue>,
)

interface ProfileStore {
    fun read(group: ProfileGroup): ConfigurationProfile?
    fun write(profile: ConfigurationProfile)
}
