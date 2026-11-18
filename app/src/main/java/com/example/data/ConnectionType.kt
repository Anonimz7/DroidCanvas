package com.example.data

// Map view: the 8 connection types offered by Kanvaz when two nodes are
// connected (K3 — used exactly as observed, 2026-11-18). Each type has its
// own color; edges are drawn as plain lines in that color, without arrows
// (K4). The hex values below are approximations of the colors seen in the
// Kanvaz screenshots (Material 300/500 shades), not sampled values.
// colorArgb is a plain ARGB Long so this data-layer enum stays free of
// Compose — UI converts it with androidx.compose.ui.graphics.Color(argb).
enum class ConnectionType(val label: String, val colorArgb: Long) {
    RELATED_TO("Related To", 0xFFB39DDB),       // light purple
    INSPIRED_BY("Inspired By", 0xFF9575CD),    // purple
    DERIVED_FROM("Derived From", 0xFF64B5F6),  // blue
    ALTERNATIVE_TO("Alternative To", 0xFFFFB74D), // amber/orange
    SUPPORTS("Supports", 0xFF81C784),          // green
    USED_IN("Used In", 0xFFE57373),            // red
    REFERENCES("References", 0xFFF06292),      // pink
    PLAIN("Plain", 0xFF9E9E9E);                // grey

    companion object {
        fun fromName(name: String): ConnectionType =
            entries.firstOrNull { it.name == name } ?: PLAIN
    }
}
