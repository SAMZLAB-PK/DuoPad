package com.unipoint.core.util

/**
 * Complete USB HID Usage ID table for Keyboard/Keypad (Usage Page 0x07).
 * Official USB HID Usage Tables – Keyboard/Keypad Page.
 *
 * These are the exact codes that Windows, macOS, Linux, Android TV understand
 * when the phone acts as a Bluetooth HID keyboard.
 */
object HidKeycodes {

    // Modifier bits (first byte of keyboard report)
    const val MOD_LEFT_CTRL   = 0x01
    const val MOD_LEFT_SHIFT  = 0x02
    const val MOD_LEFT_ALT    = 0x04
    const val MOD_LEFT_GUI    = 0x08   // Win / Cmd
    const val MOD_RIGHT_CTRL  = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT   = 0x40
    const val MOD_RIGHT_GUI   = 0x80

    // Letters
    const val KEY_A = 0x04
    const val KEY_B = 0x05
    const val KEY_C = 0x06
    const val KEY_D = 0x07
    const val KEY_E = 0x08
    const val KEY_F = 0x09
    const val KEY_G = 0x0A
    const val KEY_H = 0x0B
    const val KEY_I = 0x0C
    const val KEY_J = 0x0D
    const val KEY_K = 0x0E
    const val KEY_L = 0x0F
    const val KEY_M = 0x10
    const val KEY_N = 0x11
    const val KEY_O = 0x12
    const val KEY_P = 0x13
    const val KEY_Q = 0x14
    const val KEY_R = 0x15
    const val KEY_S = 0x16
    const val KEY_T = 0x17
    const val KEY_U = 0x18
    const val KEY_V = 0x19
    const val KEY_W = 0x1A
    const val KEY_X = 0x1B
    const val KEY_Y = 0x1C
    const val KEY_Z = 0x1D

    // Numbers
    const val KEY_1 = 0x1E
    const val KEY_2 = 0x1F
    const val KEY_3 = 0x20
    const val KEY_4 = 0x21
    const val KEY_5 = 0x22
    const val KEY_6 = 0x23
    const val KEY_7 = 0x24
    const val KEY_8 = 0x25
    const val KEY_9 = 0x26
    const val KEY_0 = 0x27

    // Control keys
    const val KEY_ENTER      = 0x28
    const val KEY_ESCAPE     = 0x29
    const val KEY_BACKSPACE  = 0x2A
    const val KEY_TAB        = 0x2B
    const val KEY_SPACE      = 0x2C
    const val KEY_MINUS      = 0x2D
    const val KEY_EQUAL      = 0x2E
    const val KEY_LEFTBRACE  = 0x2F
    const val KEY_RIGHTBRACE = 0x30
    const val KEY_BACKSLASH  = 0x31
    const val KEY_SEMICOLON  = 0x33
    const val KEY_APOSTROPHE = 0x34
    const val KEY_GRAVE      = 0x35
    const val KEY_COMMA      = 0x36
    const val KEY_DOT        = 0x37
    const val KEY_SLASH      = 0x38
    const val KEY_CAPSLOCK   = 0x39

    // Function keys
    const val KEY_F1  = 0x3A
    const val KEY_F2  = 0x3B
    const val KEY_F3  = 0x3C
    const val KEY_F4  = 0x3D
    const val KEY_F5  = 0x3E
    const val KEY_F6  = 0x3F
    const val KEY_F7  = 0x40
    const val KEY_F8  = 0x41
    const val KEY_F9  = 0x42
    const val KEY_F10 = 0x43
    const val KEY_F11 = 0x44
    const val KEY_F12 = 0x45

    // Navigation
    const val KEY_PRINTSCREEN = 0x46
    const val KEY_SCROLLLOCK  = 0x47
    const val KEY_PAUSE       = 0x48
    const val KEY_INSERT      = 0x49
    const val KEY_HOME        = 0x4A
    const val KEY_PAGEUP      = 0x4B
    const val KEY_DELETE      = 0x4C
    const val KEY_END         = 0x4D
    const val KEY_PAGEDOWN    = 0x4E
    const val KEY_RIGHT       = 0x4F
    const val KEY_LEFT        = 0x50
    const val KEY_DOWN        = 0x51
    const val KEY_UP          = 0x52

    // Numpad
    const val KEY_NUMLOCK     = 0x53
    const val KEY_KPSLASH     = 0x54
    const val KEY_KPASTERISK  = 0x55
    const val KEY_KPMINUS     = 0x56
    const val KEY_KPPLUS      = 0x57
    const val KEY_KPENTER     = 0x58
    const val KEY_KP1         = 0x59
    const val KEY_KP2         = 0x5A
    const val KEY_KP3         = 0x5B
    const val KEY_KP4         = 0x5C
    const val KEY_KP5         = 0x5D
    const val KEY_KP6         = 0x5E
    const val KEY_KP7         = 0x5F
    const val KEY_KP8         = 0x60
    const val KEY_KP9         = 0x61
    const val KEY_KP0         = 0x62
    const val KEY_KPDOT       = 0x63

    // Application / System
    const val KEY_APPLICATION = 0x65
    const val KEY_POWER       = 0x66
    const val KEY_KPEQUAL     = 0x67

    // Media (Consumer page – some hosts accept these via keyboard report)
    // Better sent as Consumer Control reports, but many systems map these:
    const val KEY_MUTE        = 0x7F
    const val KEY_VOLUMEUP    = 0x80
    const val KEY_VOLUMEDOWN  = 0x81

    /**
     * Convert a single character to HID usage ID + required modifiers.
     * Returns Pair(keycode, modifiers)
     */
    fun charToHid(c: Char): Pair<Int, Int> {
        return when (c) {
            in 'a'..'z' -> (KEY_A + (c - 'a')) to 0
            in 'A'..'Z' -> (KEY_A + (c - 'A')) to MOD_LEFT_SHIFT
            in '1'..'9' -> (KEY_1 + (c - '1')) to 0
            '0' -> KEY_0 to 0

            '!' -> KEY_1 to MOD_LEFT_SHIFT
            '@' -> KEY_2 to MOD_LEFT_SHIFT
            '#' -> KEY_3 to MOD_LEFT_SHIFT
            '$' -> KEY_4 to MOD_LEFT_SHIFT
            '%' -> KEY_5 to MOD_LEFT_SHIFT
            '^' -> KEY_6 to MOD_LEFT_SHIFT
            '&' -> KEY_7 to MOD_LEFT_SHIFT
            '*' -> KEY_8 to MOD_LEFT_SHIFT
            '(' -> KEY_9 to MOD_LEFT_SHIFT
            ')' -> KEY_0 to MOD_LEFT_SHIFT

            '-' -> KEY_MINUS to 0
            '_' -> KEY_MINUS to MOD_LEFT_SHIFT
            '=' -> KEY_EQUAL to 0
            '+' -> KEY_EQUAL to MOD_LEFT_SHIFT
            '[' -> KEY_LEFTBRACE to 0
            '{' -> KEY_LEFTBRACE to MOD_LEFT_SHIFT
            ']' -> KEY_RIGHTBRACE to 0
            '}' -> KEY_RIGHTBRACE to MOD_LEFT_SHIFT
            '\\' -> KEY_BACKSLASH to 0
            '|' -> KEY_BACKSLASH to MOD_LEFT_SHIFT
            ';' -> KEY_SEMICOLON to 0
            ':' -> KEY_SEMICOLON to MOD_LEFT_SHIFT
            '\'' -> KEY_APOSTROPHE to 0
            '"' -> KEY_APOSTROPHE to MOD_LEFT_SHIFT
            '`' -> KEY_GRAVE to 0
            '~' -> KEY_GRAVE to MOD_LEFT_SHIFT
            ',' -> KEY_COMMA to 0
            '<' -> KEY_COMMA to MOD_LEFT_SHIFT
            '.' -> KEY_DOT to 0
            '>' -> KEY_DOT to MOD_LEFT_SHIFT
            '/' -> KEY_SLASH to 0
            '?' -> KEY_SLASH to MOD_LEFT_SHIFT
            ' ' -> KEY_SPACE to 0
            '\n' -> KEY_ENTER to 0
            '\t' -> KEY_TAB to 0

            else -> KEY_SPACE to 0 // fallback
        }
    }

    /**
     * Human-readable label → HID keycode (for virtual keyboard buttons)
     */
    val LABEL_TO_HID: Map<String, Int> = mapOf(
        "Esc" to KEY_ESCAPE,
        "F1" to KEY_F1, "F2" to KEY_F2, "F3" to KEY_F3, "F4" to KEY_F4,
        "F5" to KEY_F5, "F6" to KEY_F6, "F7" to KEY_F7, "F8" to KEY_F8,
        "F9" to KEY_F9, "F10" to KEY_F10, "F11" to KEY_F11, "F12" to KEY_F12,
        "`" to KEY_GRAVE,
        "1" to KEY_1, "2" to KEY_2, "3" to KEY_3, "4" to KEY_4, "5" to KEY_5,
        "6" to KEY_6, "7" to KEY_7, "8" to KEY_8, "9" to KEY_9, "0" to KEY_0,
        "-" to KEY_MINUS, "=" to KEY_EQUAL, "Bksp" to KEY_BACKSPACE,
        "Tab" to KEY_TAB,
        "Q" to KEY_Q, "W" to KEY_W, "E" to KEY_E, "R" to KEY_R, "T" to KEY_T,
        "Y" to KEY_Y, "U" to KEY_U, "I" to KEY_I, "O" to KEY_O, "P" to KEY_P,
        "[" to KEY_LEFTBRACE, "]" to KEY_RIGHTBRACE, "\\" to KEY_BACKSLASH,
        "Caps" to KEY_CAPSLOCK,
        "A" to KEY_A, "S" to KEY_S, "D" to KEY_D, "F" to KEY_F, "G" to KEY_G,
        "H" to KEY_H, "J" to KEY_J, "K" to KEY_K, "L" to KEY_L,
        ";" to KEY_SEMICOLON, "'" to KEY_APOSTROPHE, "Enter" to KEY_ENTER,
        "Shift" to KEY_LEFTBRACE, // handled as modifier separately
        "Z" to KEY_Z, "X" to KEY_X, "C" to KEY_C, "V" to KEY_V, "B" to KEY_B,
        "N" to KEY_N, "M" to KEY_M, "," to KEY_COMMA, "." to KEY_DOT, "/" to KEY_SLASH,
        "Ctrl" to 0, "Win" to 0, "Alt" to 0, "Space" to KEY_SPACE,
        "↑" to KEY_UP, "↓" to KEY_DOWN, "←" to KEY_LEFT, "→" to KEY_RIGHT,
        "Home" to KEY_HOME, "End" to KEY_END, "PgUp" to KEY_PAGEUP, "PgDn" to KEY_PAGEDOWN,
        "Ins" to KEY_INSERT, "Del" to KEY_DELETE
    )
}
