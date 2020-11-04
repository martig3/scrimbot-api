package com.martige.service

import com.martige.model.MatchData
import com.martige.model.Stats
import model.Match
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class StatisticsService {

    fun uploadStatistics(match: Match) {
        val matchId = match.id
        match.player_stats?.forEach {
            if (!it.steam_id.trim().startsWith("STEAM", true)) {
                return@forEach
            }
            val steamIdUpdated = it.steam_id.trim().replaceRange(6, 7, "1")
            val kills = it.kills
            val assists = it.assists
            val deaths = it.deaths
            transaction {
                MatchData.insert { data ->
                    data[steamId] = steamIdUpdated
                    data[MatchData.matchId] = matchId
                    data[MatchData.kills] = kills
                    data[MatchData.deaths] = deaths
                    data[MatchData.assists] = assists
                    data[createTime] = DateTime.now()
                }
            }
        }
    }

    fun getStatistics(steamId: String): List<Stats> {
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
            ).select { MatchData.steamId.eq(steamId) }.groupBy(MatchData.steamId).map {
                Stats(
                    it[MatchData.steamId],
                    it[MatchData.kills.sum()] ?: 0,
                    it[MatchData.deaths.sum()] ?: 0,
                    it[MatchData.assists.sum()] ?: 0,
                    (it[MatchData.kills.sum().castTo<Float>(FloatColumnType())
                        .div(MatchData.deaths.sum().castTo(FloatColumnType()))] ?: Float.MIN_VALUE),
                )
            }
        }

    }

    fun getTopTenPlayers(): List<Stats> {
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType()))
            ).selectAll()
                .having { MatchData.kills.sum().greaterEq(200) }
                .groupBy(MatchData.steamId)
                .orderBy(
                    MatchData.kills.sum().castTo<Float>(FloatColumnType())
                        .div(MatchData.deaths.sum().castTo(FloatColumnType())) to SortOrder.DESC
                )
                .limit(10)
                .map {
                    Stats(
                        it[MatchData.steamId],
                        it[MatchData.kills.sum()] ?: 0,
                        it[MatchData.deaths.sum()] ?: 0,
                        it[MatchData.assists.sum()] ?: 0,
                        (it[MatchData.kills.sum().castTo<Float>(FloatColumnType())
                            .div(MatchData.deaths.sum().castTo(FloatColumnType()))] ?: Float.MIN_VALUE),
                    )
                }
        }
    }

    fun getTopTenPlayersMonthRange(length: Int?): List<Stats>? {
        if (length == null) {
            return null
        }
        val pastTime = DateTime().minusMonths(length)
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType()))
            ).select { MatchData.createTime.greaterEq(pastTime).and(MatchData.matchId.notLike("init%")) }
                .groupBy(MatchData.steamId)
                .orderBy(
                    MatchData.kills.sum().castTo<Float>(FloatColumnType())
                        .div(MatchData.deaths.sum().castTo(FloatColumnType())) to SortOrder.DESC
                )
                .limit(10)
                .map {
                    Stats(
                        it[MatchData.steamId],
                        it[MatchData.kills.sum()] ?: 0,
                        it[MatchData.deaths.sum()] ?: 0,
                        it[MatchData.assists.sum()] ?: 0,
                        (it[MatchData.kills.sum().castTo<Float>(FloatColumnType())
                            .div(MatchData.deaths.sum().castTo(FloatColumnType()))] ?: Float.MIN_VALUE),
                    )
                }
        }
    }

    fun getMonthRangeStats(steamId: String, length: Int?): List<Stats>? {
        if (length == null) {
            return null
        }
        val pastTime = DateTime().minusMonths(length)
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
            ).select {
                MatchData.steamId.eq(steamId)
                    .and(MatchData.createTime.greaterEq(pastTime))
                    .and(MatchData.matchId.notLike("init%"))
            }
                .groupBy(MatchData.steamId).map {
                    Stats(
                        it[MatchData.steamId],
                        it[MatchData.kills.sum()] ?: 0,
                        it[MatchData.deaths.sum()] ?: 0,
                        it[MatchData.assists.sum()] ?: 0,
                        (it[MatchData.kills.sum().castTo<Float>(FloatColumnType())
                            .div(MatchData.deaths.sum().castTo(FloatColumnType()))] ?: Float.MIN_VALUE),
                    )
                }
        }
    }
}