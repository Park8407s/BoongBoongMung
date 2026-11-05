package com.kumhoht.boongboongmung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kumhoht.boongboongmung.reward.Catalog
import com.kumhoht.boongboongmung.reward.Item
import com.kumhoht.boongboongmung.reward.Slot
import com.kumhoht.boongboongmung.reward.Source
import com.kumhoht.boongboongmung.ui.pet.Expression
import com.kumhoht.boongboongmung.ui.pet.ItemIcon
import com.kumhoht.boongboongmung.ui.pet.PetView
import com.kumhoht.boongboongmung.ui.theme.Boong

/**
 * 상점·꾸미기: 모은 포인트로 아이템을 사고, 레이어로 겹쳐 장착한다. 장착 즉시 붕붕이가 반응한다.
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
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = Catalog.item(selectedId)

    // 미리보기: 선택한 아이템을 입혀 본다
    val preview = remember(wardrobe.equipped, selected) {
        if (selected == null) wardrobe.equipped else wardrobe.equipped + (selected.slot to selected.id)
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        BoongCard(Modifier.weight(0.85f).fillMaxHeight(), padding = 14.dp) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                PetView(preview, Expression.HAPPY, Modifier.fillMaxSize(), reactKey = "$reactKey/$selectedId")
            }
            VSpace(10.dp)
            Text(
                selected?.name ?: "미리보기",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(8.dp)
            if (selected != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ActionButton(selected, wallet, wardrobe, onBuy, onEquip, onUnequip)
                }
            }
        }

        BoongCard(Modifier.weight(1.25f).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Slot.entries.forEach { s ->
                    Chip(s.label, s == slot, onClick = { slot = s; selectedId = null })
                }
            }
            VSpace(16.dp)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(Catalog.bySlot(slot), key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        owned = item.id in wardrobe.owned,
                        equipped = wardrobe.equipped[item.slot] == item.id,
                        selected = item.id == selectedId,
                        locked = wallet.progress.level < item.requiredLevel,
                        onClick = { selectedId = item.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    item: Item,
    wallet: Wallet,
    wardrobe: Wardrobe,
    onBuy: (Item) -> Unit,
    onEquip: (Item) -> Unit,
    onUnequip: (Slot) -> Unit,
) {
    val owned = item.id in wardrobe.owned
    val equipped = wardrobe.equipped[item.slot] == item.id
    when {
        equipped && item.id != Catalog.DEFAULT_BACKGROUND -> BoongButton("벗기", { onUnequip(item.slot) }, color = Boong.BrownSoft)
        equipped -> BoongButton("장착 중", {}, enabled = false)
        owned -> BoongButton("장착하기", { onEquip(item) })
        item.source == Source.TITLE -> BoongButton("칭호 '${Catalog.SMOOTH_TITLE}' 보상", {}, enabled = false)
        wallet.progress.level < item.requiredLevel -> BoongButton("Lv.${item.requiredLevel}부터", {}, enabled = false)
        else -> BoongButton("구매 · ${item.price.withComma()}", { onBuy(item) }, enabled = wallet.balance >= item.price)
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
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Boong.Cream)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Boong.Brown else Boong.CardLine,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f), contentAlignment = Alignment.Center) {
            ItemIcon(item, Modifier.fillMaxSize())
            if (locked && !owned) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Boong.Cream.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) { Text("Lv.${item.requiredLevel}", style = MaterialTheme.typography.titleMedium) }
            }
        }
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        when {
            equipped -> Text("장착 중", style = MaterialTheme.typography.labelMedium)
            owned -> Text("보유", style = MaterialTheme.typography.labelMedium)
            item.source == Source.TITLE -> Text("칭호 보상", style = MaterialTheme.typography.labelMedium)
            item.price == 0 -> Text("기본", style = MaterialTheme.typography.labelMedium)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                BoneIcon(Modifier.size(20.dp))
                HSpace(4.dp)
                Text(item.price.withComma(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
