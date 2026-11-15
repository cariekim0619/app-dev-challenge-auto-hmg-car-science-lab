package com.carsciencelab.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * 화면의 글자는 모두 이 컴포넌트로 그린다.
 *
 * Android는 한글을 글자 단위로 줄바꿈할 수 있어서, 그대로 두면 ‘깜빡일까/요?’처럼 어절 중간에서
 * 줄이 넘어간다. 어린이가 읽는 문장이므로 어절이 쪼개지지 않게 한다. 기기 언어나 Android 버전에
 * 기대지 않도록, 어절 안의 글자 사이에 보이지 않는 ‘단어 결합자’를 넣는 방식을 쓴다.
 *
 * 화면 낭독기와 접근성 도구에는 원래 문장을 그대로 알린다.
 */
@Composable
fun LabText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val shown = remember(text) { keepWordsTogether(text) }
    Text(
        text = shown,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** 줄바꿈을 막는 보이지 않는 글자(U+2060). */
private const val WORD_JOINER = '⁠'

/** 이보다 긴 덩어리는 그대로 둔다. 한 줄에 다 들어가지 못하면 넘치기 때문이다. */
private const val MAX_JOINED_LENGTH = 14

/**
 * 공백으로 나뉜 덩어리(어절) 안에서는 줄이 바뀌지 않게 글자 사이에 단어 결합자를 넣는다.
 * 줄바꿈은 공백에서만 일어난다.
 */
internal fun keepWordsTogether(text: String): String {
    if (text.length < 2) return text
    val out = StringBuilder(text.length * 2)
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            out.append(text[index])
            index += 1
            continue
        }
        var end = index
        while (end < text.length && !text[end].isWhitespace()) end += 1
        if (end - index > MAX_JOINED_LENGTH) {
            out.append(text, index, end)
        } else {
            for (position in index until end) {
                // 대리 쌍으로 적는 글자(이모지 등)는 한 글자이므로 사이에 넣지 않는다.
                if (position > index && !Character.isLowSurrogate(text[position])) out.append(WORD_JOINER)
                out.append(text[position])
            }
        }
        index = end
    }
    return out.toString()
}
