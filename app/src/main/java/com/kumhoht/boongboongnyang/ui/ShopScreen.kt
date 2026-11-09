package com.kumhoht.boongboongnyang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kumhoht.boongboongnyang.reward.Catalog
import com.kumhoht.boongboongnyang.reward.Item
import com.kumhoht.boongboongnyang.reward.Slot
import com.kumhoht.boongboongnyang.reward.Source
import com.kumhoht.boongboongnyang.ui.pet.Expression
import com.kumhoht.boongboongnyang.ui.pet.ItemIcon
import com.kumhoht.boongboongnyang.ui.pet.PetView
import com.kumhoht.boongboongnyang.ui.theme.Boong
import com.kumhoht.boongboongnyang.ui.theme.Jua

/**
 * 상점 — 디자인 캔버스 v3 '상점'.
 * 왼쪽: 고른 아이템을 입혀 본 붕붕냥 / 오른쪽: 분류 탭 · 3열 아이템 카드 · 선택 아이템 구매 막대
 */
@Composable
fun ShopScreen(
    wallet: Wallet,
    wardrobe: Wardrobe,
    reactKey: Int,
    onBuy: (Item) -> Unit,
    onEquip: (Item) -> Unit,
    onUnequip: (Slot) -> Unit,
) {
    var slot by remember { mutableStateOf(Slot.HAT) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    // 고른 게 없으면 그 분류에서 장착 중인 아이템(없으면 첫 번째)을 보여 준다
    val selected = Catalog.item(pickedId)?.takeIf { it.slot == slot }
        ?: Catalog.item(wardrobe.equipped[slot]) ?: Catalog.bySlot(slot).first()

    val preview = remember(wardrobe.equipped, selected) { wardrobe.equipped + (selected.slot to selected.id) }
    val selEquipped = wardrobe.equipped[selected.slot] == selected.id

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // 왼쪽: 미리보기 (폭 720)
        Box(Modifier.width(720.dp).fillMaxHeight().clip(RoundedCornerShape(44.dp))) {
            PetView(preview, Expression.HAPPY, Modifier.fillMaxSize(), reactKey = "$reactKey/${selected.id}")
            SpeechBubble(Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 44.dp, end = 44.dp)) {
                Text("${selected.name} 잘 어울려요?", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Boong.Brown)
                val worn = Slot.entries.mapNotNull { s -> Catalog.item(wardrobe.equipped[s])?.takeIf { s != Slot.BACKGROUND } }
                Text(
                    if (worn.isEmpty()) "장착 중 · 없음" else "장착 중 · " + worn.joinToString(", ") { it.name },
                    fontSize = 24.sp, color = Boong.Muted, maxLines = 1,
                )
            }
        }

        // 오른쪽: 상점 카드
        BoongCard(Modifier.weight(1f).fillMaxHeight(), padding = 40.dp, radius = 44.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slot.entries.forEach { s ->
                    Chip(s.label, s == slot, onClick = { slot = s; pickedId = null }, modifier = Modifier.weight(1f))
                }
            }
            VSpace(28.dp)
            val items = Catalog.bySlot(slot)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                for (row in 0 until 2) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (col in 0 until 3) {
                            val item = items.getOrNull(row * 3 + col)
                            if (item == null) Spacer(Modifier.weight(1f))
                            else ItemCard(
                                item = item,
                                owned = item.id in wardrobe.owned,
                                equipped = wardrobe.equipped[item.slot] == item.id,
                                selected = item.id == selected.id,
                                locked = wallet.progress.level < item.requiredLevel && item.id !in wardrobe.owned,
                                onClick = { pickedId = item.id },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            VSpace(28.dp)
            // 선택 아이템 막대
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Boong.Chip)
                    .padding(start = 32.dp, top = 22.dp, bottom = 22.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("선택한 아이템", fontSize = 24.sp, color = Boong.ChipText)
                    Text(selected.name, fontFamily = Jua, fontSize = if (selected.name.length > 5) 34.sp else 42.sp, color = Boong.Brown, maxLines = 1, softWrap = false)
                }
                val canBuy = selected.id !in wardrobe.owned && selected.source != Source.TITLE && selected.price > 0
                if (canBuy && wallet.progress.level >= selected.requiredLevel) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("구매 후 남는 포인트", fontSize = 24.sp, color = Boong.ChipText)
                        val left = wallet.balance - selected.price
                        Text(if (left >= 0) left.withComma() else "${(-left).withComma()} 부족", fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (left >= 0) Boong.Brown else Boong.Danger)
                    }
                    HSpace(28.dp)
                }
                ActionButton(selected, selEquipped, wallet, wardrobe, onBuy, onEquip, onUnequip)
            }
        }
    }
}

@Composable
private fun ActionButton(
    item: Item,
    equipped: Boolean,
    wallet: Wallet,
    wardrobe: Wardrobe,
    onBuy: (Item) -> Unit,
    onEquip: (Item) -> Unit,
    onUnequip: (Slot) -> Unit,
) {
    val owned = item.id in wardrobe.owned
    val h = 84.dp
    when {
        equipped && item.id != Catalog.DEFAULT_BACKGROUND -> BoongButton("벗기", { onUnequip(item.slot) }, color = Boong.BrownSoft, height = h, fontSize = 34)
        equipped -> BoongButton("장착 중", {}, enabled = false, height = h, fontSize = 34)
        owned -> BoongButton("장착하기", { onEquip(item) }, height = h, fontSize = 34)
        item.source == Source.TITLE -> BoongButton("칭호 보상", {}, enabled = false, height = h, fontSize = 34)
        wallet.progress.level < item.requiredLevel -> BoongButton("Lv.${item.requiredLevel}부터", {}, enabled = false, height = h, fontSize = 34)
        else -> BoongButton(
            "${item.price.withComma()} 구매하기", { onBuy(item) }, enabled = wallet.balance >= item.price, height = h, fontSize = 34,
            leading = { BoneIcon(Modifier.size(36.dp)) },
        )
    }
}

@Composable
private fun ItemCard(
    item: Item,
    owned: Boolean,
    equipped: Boolean,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (locked) Color(0xFFFBF5EE) else if (selected) Boong.Cream else Boong.Card)
            .border(if (selected) 4.dp else 2.dp, if (selected) Boong.Accent else Boong.CardLine, shape)
            .clickable(onClick = onClick)
            .padding(if (selected) 16.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (locked) Color(0xFFF3E9DE) else if (selected) Color.White else Boong.Cream),
            contentAlignment = Alignment.Center,
        ) {
            ItemIcon(item, Modifier.fillMaxSize().padding(if (item.slot == Slot.BACKGROUND) 0.dp else 12.dp).alpha(if (locked) 0.45f else 1f))
            if (locked) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Boong.Brown), contentAlignment = Alignment.Center) {
                    BoongIcon(Icon.Lock, Boong.Cream, Modifier.size(34.dp), strokeWidth = 2.2f)
                }
            }
        }
        Text(item.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Boong.Brown, maxLines = 1)
        Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
            when {
                equipped -> Pill(bg = Boong.GoodBg, height = 44.dp, padH = 18.dp) { Text("장착 중", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Boong.Good) }
                owned -> Pill(bg = Boong.Nav, height = 44.dp, padH = 18.dp) { Text("보유 중", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Boong.BrownSoft) }
                locked -> Text("Lv.${item.requiredLevel}에 열려요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Boong.Muted)
                item.source == Source.TITLE -> Text("칭호 보상", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Boong.Muted)
                item.price == 0 -> Text("기본", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Boong.Muted)
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BoneIcon(Modifier.size(32.dp))
                    Text(item.price.withComma(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Boong.Brown)
                }
            }
        }
    }
}

