package com.hack.kidhack.core.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Digits only (max 11). */
fun digitsOnlyPhone(raw: String): String = raw.filter { it.isDigit() }.take(11)

/** 휴대폰 식별번호(010·011·016·017·018·019) + 국번 3~4자리 + 뒷번호 4자리. 숫자만 넣은 값 기준. */
private val MOBILE_PHONE_PATTERN = Regex("""^01[016789]\d{7,8}$""")

/**
 * 회원가입에 쓸 수 없는 휴대폰 번호면 거부 사유를 돌려주고, 쓸 수 있으면 null 을 돌려준다.
 *
 * 형식만 맞는 가짜 번호(010-1234-1234 처럼 아무렇게나 친 번호)를 가입 전에 막는 클라이언트 검사다.
 * 서버 검증을 대신하지는 않는다. 입력 편의용 1차 필터다.
 *
 * 거부 규칙 (숫자만 남긴 뒤 검사한다):
 * 1. 형식: 01X 로 시작해야 하고, 010 은 반드시 11자리여야 한다.
 * 2. 국번(가운데 자리)이 전부 0 (010-0000-5678): 실제로 개통되지 않는 번호다.
 * 3. 뒤 8자리가 4자리 블록 반복 (1234-1234, 5678-5678).
 * 4. 뒤 8자리가 전부 같은 숫자 (1111-1111, 0000-0000).
 * 5. 뒤 8자리가 1씩 오르거나 내리는 연속 숫자 (1234-5678, 8765-4321).
 */
fun phoneNumberRejectReason(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    if (!MOBILE_PHONE_PATTERN.matches(digits)) return "올바른 휴대폰 번호 형식이 아니에요"
    // 010 은 번호이동 이후 11자리만 있다. 010 + 7자리(10자리)는 존재하지 않는다.
    if (digits.startsWith("010") && digits.length != 11) return "올바른 휴대폰 번호 형식이 아니에요"

    // 식별번호(앞 3자리)를 뺀 나머지: 국번 3~4자리 + 뒷번호 4자리.
    val subscriber = digits.substring(3)
    val middle = subscriber.dropLast(4)
    val last = subscriber.takeLast(4)

    val meaningless = when {
        middle.all { it == '0' } -> true
        middle == last -> true // 1234-1234: 국번과 뒷번호가 같은 블록 반복
        subscriber.toSet().size == 1 -> true // 1111-1111
        isSequentialDigits(subscriber) -> true // 1234-5678, 8765-4321
        else -> false
    }
    return if (meaningless) "유효하지 않은 휴대폰 번호예요. 실제 번호를 입력해 주세요" else null
}

/** 모든 인접 숫자 차이가 +1 이거나 모두 -1 이면 연속 숫자로 본다. */
private fun isSequentialDigits(s: String): Boolean {
    if (s.length < 2) return false
    val diffs = s.zipWithNext { a, b -> b - a }
    return diffs.all { it == 1 } || diffs.all { it == -1 }
}

/** Keeps digits only (max 11) and formats as 010-1234-5678. */
fun formatPhoneNumber(raw: String): String {
    val digits = digitsOnlyPhone(raw)
    return when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
        else -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
    }
}

/**
 * 상태에는 숫자만 두고, 화면에는 하이픈을 그린다.
 * 값 문자열에 `-`를 넣으면 커서가 밀리므로 가입/찾기 입력은 이 transformation을 쓴다.
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = digitsOnlyPhone(text.text)
        val formatted = formatPhoneNumber(digits)
        return TransformedText(
            AnnotatedString(formatted),
            PhoneOffsetMapping(digitCount = digits.length, formattedLength = formatted.length),
        )
    }
}

private class PhoneOffsetMapping(
    private val digitCount: Int,
    private val formattedLength: Int,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, digitCount)
        val mapped = when {
            o <= 3 -> o
            o <= 7 -> o + 1
            else -> o + 2
        }
        return mapped.coerceIn(0, formattedLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val t = offset.coerceIn(0, formattedLength)
        val mapped = when {
            t <= 3 -> t
            t <= 8 -> t - 1
            else -> t - 2
        }
        return mapped.coerceIn(0, digitCount)
    }
}
