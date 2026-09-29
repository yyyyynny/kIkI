package com.langsense.app.util

import com.langsense.app.util.SettingsSearch.Item
import com.langsense.app.util.SettingsSearch.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val items = listOf(
        Item("상시 배지 표시", "상시 배지", "badge", listOf("배지", "표시", "켜기")),
        Item("배지 배경색", "상시 배지", "badge", listOf("색", "컬러", "배경", "color")),
        Item("배경 불투명도", "상시 배지", "badge", listOf("투명", "알파", "흐리게", "opacity")),
        Item("경고 켜기", "입력 경고", "nofocus", listOf("선택되지 않음", "포커스", "focus")),
        Item("화면 테마", "화면 테마", "theme", listOf("다크", "dark", "라이트", "light")),
    )

    private fun names(q: String) = SettingsSearch.search(items, q).hits.map { it.item.name }

    @Test
    fun multiWord_isAnd_acrossFields() {
        // "배지"는 경로/이름, "색"은 태그 — 한 덩어리 부분일치로는 못 찾던 조합
        assertEquals(listOf("배지 배경색"), names("배지 색"))
    }

    @Test
    fun spacesAreIgnored() {
        assertEquals(listOf("경고 켜기"), names("선택되지않음"))
        assertEquals(listOf("상시 배지 표시"), names("배지표시"))
    }

    @Test
    fun choseong() {
        assertTrue(names("ㅂㅈ").contains("상시 배지 표시"))
        assertEquals(listOf("화면 테마"), names("ㅎㅁㅌㅁ"))
    }

    @Test
    fun nameMatchRanksAboveTagAndPath() {
        // "배경"은 "배경 불투명도"의 이름 앞부분, "배지 배경색"의 이름 중간·태그 — 이름 앞부분이 먼저
        assertEquals("배경 불투명도", names("배경").first())
    }

    @Test
    fun tagHit_reportsMatchedTag() {
        val hit = SettingsSearch.search(items, "알파").hits.single()
        assertEquals("배경 불투명도", hit.item.name)
        assertEquals("알파", hit.matchedTag)
        val byName = SettingsSearch.search(items, "불투명").hits.single()
        assertNull(byName.matchedTag)
    }

    @Test
    fun keyboardFix_englishTypedForKorean() {
        val r = SettingsSearch.search(items, "qowl") // 배지
        assertEquals(Mode.KEYBOARD_FIX, r.mode)
        assertEquals("배지", r.corrected)
        assertTrue(r.hits.any { it.item.name == "상시 배지 표시" })
    }

    @Test
    fun keyboardFix_koreanTypedForEnglish() {
        val r = SettingsSearch.search(items, "ㅇㅁ가") // dark
        assertEquals(Mode.KEYBOARD_FIX, r.mode)
        assertEquals(listOf("화면 테마"), r.hits.map { it.item.name })
    }

    @Test
    fun keyboardFix_onlyWhenDirectFindsNothing() {
        // "rk"(→가) 같은 짧은 영문이 직접 일치하지 않는다고 흔한 음절로 둔갑해 엉뚱한 결과가 쏟아지면 안 됨
        val r = SettingsSearch.search(items, "color")
        assertEquals(Mode.DIRECT, r.mode)
        assertEquals(listOf("배지 배경색"), r.hits.map { it.item.name })
    }

    @Test
    fun typo_oneJamoOff() {
        val r = SettingsSearch.search(items, "배치") // 배지
        assertEquals(Mode.TYPO, r.mode)
        assertTrue(r.hits.any { it.item.name == "상시 배지 표시" })
    }

    @Test
    fun noMatch_isEmpty() {
        assertTrue(SettingsSearch.search(items, "블루투스").hits.isEmpty())
        assertTrue(SettingsSearch.search(items, "   ").hits.isEmpty())
    }

    @Test
    fun editDistance() {
        assertTrue(SettingsSearch.editDistanceAtMostOne("abc", "abc"))
        assertTrue(SettingsSearch.editDistanceAtMostOne("abc", "axc"))
        assertTrue(SettingsSearch.editDistanceAtMostOne("abc", "abxc"))
        assertTrue(SettingsSearch.editDistanceAtMostOne("abc", "ab"))
        assertFalse(SettingsSearch.editDistanceAtMostOne("abc", "xyc"))
        assertFalse(SettingsSearch.editDistanceAtMostOne("abc", "a"))
    }

    @Test
    fun jamoAndChoseongHelpers() {
        assertEquals("ㅂㅐㅈㅣ", SettingsSearch.jamo("배지"))
        assertEquals("ㄱㅗㅏ", SettingsSearch.jamo("과"))
        assertEquals("ㅂㅈ", SettingsSearch.choseong("배지"))
    }
}
