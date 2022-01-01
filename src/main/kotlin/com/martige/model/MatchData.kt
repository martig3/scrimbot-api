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
    val mapName: Column<String> = text("map_name")
    val hs: Column<Double> = double("hs")
    val rws: Column<Double> = double("rws")
    val adr: Column<Double> = double("adr")
    val rating: Column<Double> = double("rating")
    val effFlashes: Column<Int> = integer("eff_flashes")
    val efpr: Column<Double> = double("efpr")
    val matchResult: Column<String> = text("match_result")
}

data class Stats(
    val steamId: String,
    val totalKills: Int,
    val totalDeaths: Int,
    val totalAssists: Int,
    var kdRatio: Float,
    val map: String,
    var hs: Float,
    var rws: Float,
    var adr: Float,
    var rating: Float,
    var efpr: Float,
    var playCount: Int,
    var winPercentage: Float,
)