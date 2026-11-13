package io.openhoyi.mobile

/** Official fSwChange transition. Construction/loading intentionally leaves both recipe bits untouched. */
internal data class CurveModeSelection(val flow: Boolean, val pressure: Boolean, val pressureBeforeFlow: Boolean = false) {
    fun selectFlow(selected: Boolean): CurveModeSelection {
        if (selected == flow) return this
        return if (selected) CurveModeSelection(true, true, pressure)
        else copy(flow = false, pressure = pressure && pressureBeforeFlow)
    }
}
