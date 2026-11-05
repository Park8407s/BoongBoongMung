package com.kumhoht.boongboongmung.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 운행 기록 1건 (차량 로컬에만 저장) */
@Entity(tableName = "trips", indices = [Index("endAt")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,
    val endAt: Long,
    val drivingMs: Long,
    val distanceKm: Double,
    val smoothKm: Int,
    val judgedKm: Int,
    val harshAccel: Int,
    val harshDecel: Int,
    /** 규정 속도 준수 비율 0~1. 내비 미연동이면 null */
    val speedLimitRatio: Double?,
    val arrived: Boolean,
    val points: Int,
    val giftBoxes: Int,
    /** 정산 줄 목록 "라벨\t포인트\n…" */
    val lines: String,
    val levelBefore: Int,
    val levelAfter: Int,
    val restBonus: Boolean,
    val feedback: String,
    /** 결과 화면을 봤는지 */
    val seen: Boolean = false,
    /** 주행 로그 재생 모드로 만든 기록 */
    val replay: Boolean = false,
)

/** 부드러운 운전 비율(%). 판정 구간이 없으면 null */
fun TripEntity.smoothPercent(): Int? = if (judgedKm > 0) smoothKm * 100 / judgedKm else null

/** 뼈다귀 포인트 입출금 내역. 잔액 = 합계 */
@Entity(tableName = "ledger")
data class LedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val amount: Int,
    val reason: String,
)

/** 보유 아이템 */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val itemId: String,
    val acquiredAt: Long,
    val source: String,
)

/** 슬롯별 장착 아이템 */
@Entity(tableName = "equipped")
data class EquippedEntity(
    @PrimaryKey val slot: String,
    val itemId: String,
)

/** 선물상자. 안전운전으로만 얻는다 (구매 불가). */
@Entity(tableName = "gift_boxes")
data class GiftBoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val earnedAt: Long,
    val tripId: Long,
    val openedAt: Long? = null,
    /** "bones:30" 또는 "item:glasses_sun" */
    val reward: String? = null,
)

/** 획득한 칭호 (레벨 칭호는 계산, 업적 칭호만 저장) */
@Entity(tableName = "titles")
data class TitleEntity(
    @PrimaryKey val title: String,
    val earnedAt: Long,
)

/** 진행 상태 등 단순 값 */
@Entity(tableName = "kv")
data class KvEntity(
    @PrimaryKey val key: String,
    val value: String,
)
