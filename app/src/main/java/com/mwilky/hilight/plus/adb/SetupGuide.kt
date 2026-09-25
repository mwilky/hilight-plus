package com.mwilky.hilight.plus.adb

/** Where the user is in setting up the built-in connection, derived purely from system state. */
enum class SetupStep {
    DEV_OPTIONS,
    WIFI,
    WIRELESS_DEBUGGING,
    PAIR,
    DONE
}

enum class PairingPhase {
    /** Waiting for the user to open "Pair device with pairing code". */
    SEARCHING,
    /** The pairing dialog is open, so a code can be entered. */
    CODE_NEEDED,
    PAIRING,
    WRONG_CODE,
    /** Paired; starting the daemon. */
    STARTING,
    FAILED
}

data class SetupState(
    val devOptionsOn: Boolean = false,
    val wifiConnected: Boolean = false,
    val wirelessDebuggingOn: Boolean = false,
    val pairing: PairingPhase = PairingPhase.SEARCHING,
    val codeEntryAvailable: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null
) {
    val step: SetupStep
        get() = when {
            connected -> SetupStep.DONE
            !devOptionsOn -> SetupStep.DEV_OPTIONS
            !wifiConnected -> SetupStep.WIFI
            !wirelessDebuggingOn -> SetupStep.WIRELESS_DEBUGGING
            else -> SetupStep.PAIR
        }
}

/** What the guide notification says; one per state it can be in. */
enum class GuideNotice {
    TAP_BUILD_NUMBER,
    CONNECT_WIFI,
    TURN_ON_WIRELESS_DEBUGGING,
    TAP_PAIR,
    ENTER_CODE,
    CONNECTING,
    WRONG_CODE,
    FAILED,
    DONE
}

fun SetupState.guideNotice(): GuideNotice = when (step) {
    SetupStep.DEV_OPTIONS -> GuideNotice.TAP_BUILD_NUMBER
    SetupStep.WIFI -> GuideNotice.CONNECT_WIFI
    SetupStep.WIRELESS_DEBUGGING -> GuideNotice.TURN_ON_WIRELESS_DEBUGGING
    SetupStep.DONE -> GuideNotice.DONE
    SetupStep.PAIR -> when (pairing) {
        PairingPhase.SEARCHING -> GuideNotice.TAP_PAIR
        PairingPhase.CODE_NEEDED -> GuideNotice.ENTER_CODE
        PairingPhase.PAIRING, PairingPhase.STARTING -> GuideNotice.CONNECTING
        PairingPhase.WRONG_CODE -> GuideNotice.WRONG_CODE
        PairingPhase.FAILED -> GuideNotice.FAILED
    }
}

/** Whether the notification should offer a reply field for the code. */
fun SetupState.acceptsCode(): Boolean =
    step == SetupStep.PAIR && codeEntryAvailable &&
        (pairing == PairingPhase.CODE_NEEDED || pairing == PairingPhase.WRONG_CODE)

/** The six digits from whatever was typed or pasted, or null if that isn't a pairing code. */
fun normalisePairingCode(input: String): String? =
    input.filterNot { it.isWhitespace() || it == '-' }.takeIf { it.length == 6 && it.all(Char::isDigit) }
