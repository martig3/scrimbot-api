package com.martige.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime


object MatchData: Table() {
    val steamId: Column<String> = text("steam_id")
    val matchId: Column<String> = text("match_id")
    val kills: Column<Int> = integer("kills")
    val deaths: Column<Int> = integer("deaths")
    val assists: Column<Int> = integer("assists")
    val createTime: Column<DateTime> = date("create_time")
}

data class Stats(
    val steamId: String,
    val totalKills: Int,
    val totalDeaths: Int,
    val totalAssists: Int,
    var kdRatio: Float,
)
