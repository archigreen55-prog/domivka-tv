package com.domivka.launcher

enum class TvInput { HDMI1, HDMI2, HDMI3, TV, AV }

interface InputSwitcher {
    fun switchTo(input: TvInput)
}
