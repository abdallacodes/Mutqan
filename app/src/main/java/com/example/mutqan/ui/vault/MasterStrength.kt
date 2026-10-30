package com.example.mutqan.ui.vault

enum class MasterStrength {
    WEAK, STABLE, SOLID;

    companion object {
        fun fromQuality(quality: Float): MasterStrength = when {
            quality >= 0.70f -> SOLID
            quality >= 0.40f -> STABLE
            else          -> WEAK
        }
    }
}
