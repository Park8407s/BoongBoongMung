package com.kumhoht.boongboongmung.reward

import kotlin.random.Random

/** 꾸미기 슬롯. 레이어 순서: 배경 → 카시트 → 몸 → 목도리 → 머리 → 안경 → 모자 */
enum class Slot(val label: String) {
    HAT("모자"), GLASSES("안경"), SCARF("목도리"), SEAT("카시트"), BACKGROUND("배경")
}

/** 아이템 획득 방법 */
enum class Source { SHOP, GIFT_BOX, TITLE, DEFAULT }

data class Item(
    val id: String,
    val slot: Slot,
    val name: String,
    val price: Int,
    val source: Source = Source.SHOP,
    val requiredLevel: Int = 1,
    /** 그리기용 주 색상 (ARGB) */
    val color: Long,
    val accent: Long = color,
)

/**
 * 1차 버전 아이템 13종 + 배경 3종. 그래픽은 Pet Renderer 가 레이어로 그린다.
 * 피그마 일러스트가 있는 아이템(밀짚모자·선글라스·파랑 목도리)은 이미지로, 나머지는 Canvas 로 그린다.
 */
object Catalog {
    val items: List<Item> = listOf(
        // 모자 6
        Item("hat_straw", Slot.HAT, "밀짚모자", 300, color = 0xFFF2CF7E, accent = 0xFFE8737A),
        Item("hat_beret", Slot.HAT, "베레모", 250, color = 0xFFE5737A),
        Item("hat_knit", Slot.HAT, "털모자", 200, color = 0xFF7FB2DE, accent = 0xFFFFFFFF),
        Item("hat_crown", Slot.HAT, "왕관", 800, requiredLevel = 8, color = 0xFFF4B740, accent = 0xFFE5737A),
        Item("hat_party", Slot.HAT, "생일 고깔", 150, color = 0xFFB79BE0, accent = 0xFFF4B740),
        Item("hat_cap", Slot.HAT, "캡모자", 200, color = 0xFF8FC79A),
        // 안경 2
        Item("glasses_sun", Slot.GLASSES, "선글라스", 300, source = Source.GIFT_BOX, color = 0xFF3B2A20),
        Item("glasses_round", Slot.GLASSES, "동그란 안경", 250, color = 0xFF8A6A50),
        // 목도리 3
        Item("scarf_red", Slot.SCARF, "빨간 목도리", 200, color = 0xFFE5737A, accent = 0xFFC9545C),
        Item("scarf_blue", Slot.SCARF, "파랑 목도리", 200, color = 0xFF7FB2DE),
        Item("scarf_ribbon", Slot.SCARF, "부드러운 리본", 0, source = Source.TITLE, color = 0xFFF7B9C8, accent = 0xFFE88AA3),
        // 카시트 2
        Item("seat_sky", Slot.SEAT, "하늘 카시트", 400, color = 0xFF9CC9EA),
        Item("seat_cushion", Slot.SEAT, "쿠션 카시트", 350, requiredLevel = 3, color = 0xFFF4C99B),
        // 배경 3
        Item("bg_sky", Slot.BACKGROUND, "맑은 하늘", 0, source = Source.DEFAULT, color = 0xFFDCEEF7, accent = 0xFFC3E2B8),
        Item("bg_sunset", Slot.BACKGROUND, "노을 길", 400, requiredLevel = 2, color = 0xFFF8C9A0, accent = 0xFFE59A84),
        Item("bg_night", Slot.BACKGROUND, "밤 도시", 500, requiredLevel = 5, color = 0xFF2E3A5C, accent = 0xFFF4D27A),
    )

    private val byId = items.associateBy { it.id }
    fun item(id: String?): Item? = id?.let { byId[it] }
    fun bySlot(slot: Slot) = items.filter { it.slot == slot }

    const val DEFAULT_BACKGROUND = "bg_sky"
    /** '부드러운 드라이버' 칭호 보상 */
    const val SMOOTH_TITLE_ITEM = "scarf_ribbon"
    const val SMOOTH_TITLE = "부드러운 드라이버"
}

/** 선물상자 내용물 (확률 공개). 선물상자는 안전운전으로만 얻고 금전으로 살 수 없다. */
sealed interface GiftReward {
    data class Bones(val amount: Int) : GiftReward
    data class ItemReward(val itemId: String) : GiftReward
}

object GiftBox {
    data class Odds(val label: String, val percent: Int)

    /** 화면에 그대로 공개하는 확률표 */
    val odds = listOf(
        Odds("뼈다귀 30개", 55),
        Odds("뼈다귀 60개", 30),
        Odds("아이템 1개 (이미 다 있으면 뼈다귀 120개)", 15),
    )

    /** 선물상자 전용 + 상점 아이템 중 아직 없는 것 */
    fun open(owned: Set<String>, level: Int, random: Random = Random.Default): GiftReward {
        val roll = random.nextInt(100)
        return when {
            roll < 55 -> GiftReward.Bones(30)
            roll < 85 -> GiftReward.Bones(60)
            else -> {
                val candidates = Catalog.items.filter {
                    it.id !in owned && it.requiredLevel <= level &&
                        (it.source == Source.GIFT_BOX || it.source == Source.SHOP)
                }
                // 선물상자 전용 아이템을 먼저 준다 (시나리오: 아이가 상자를 열어 선글라스 획득)
                val exclusive = candidates.filter { it.source == Source.GIFT_BOX }
                val pick = (exclusive.ifEmpty { candidates }).randomOrNull(random)
                if (pick != null) GiftReward.ItemReward(pick.id) else GiftReward.Bones(120)
            }
        }
    }
}
