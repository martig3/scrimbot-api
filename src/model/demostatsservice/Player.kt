package model.demostatsservice

data class Player(
    val adr: Double,
    val assists: Int,
    val atag: String,
    val deaths: Int,
    val effFlashes: Int,
    val firstdeaths: Int,
    val firstkills: Int,
    val flashDuration: Int,
    val headshots: Int,
    val hsprecent: Double,
    val isamember: Boolean,
    val isbot: Boolean,
    val kast: Int,
    val kastRounds: Int,
    val kd: Double,
    val kills: Int,
    val mvps: Int,
    val name: String,
    val rank: Int,
    val rating: Double,
    val rounds1k: Int,
    val rounds2k: Int,
    val rounds3k: Int,
    val rounds4k: Int,
    val rounds5k: Int,
    val roundswonv3: Int,
    val roundswonv4: Int,
    val roundswonv5: Int,
    val rws: Double,
    val steamid: String,
    val steamid64: Long,
    val team: String,
    val tradedeaths: Int,
    val tradefirstdeaths: Int,
    val tradefirstkills: Int,
    val tradekills: Int,
    val weaponStats: WeaponStats?,
    val playerDamages: PlayerDamages?,

)

data class WeaponStats(
    val kills: Map<String, Int>?,
    val headshots: Map<String, Int>?,
    val accuracy: Map<String, Int>?,
    val damage: Map<String, Int>?,
    val shots: Map<String, Int>?,
    val hits: Map<String, Int>?,
)

data class PlayerDamages(
    val damages: Map<String, Int>?
)
