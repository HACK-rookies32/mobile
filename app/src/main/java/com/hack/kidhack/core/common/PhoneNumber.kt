package com.hack.kidhack.core.common

/** Keeps digits only (max 11) and formats as 010-1234-5678. */
fun formatPhoneNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(11)
    return when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
        else -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
    }
}
