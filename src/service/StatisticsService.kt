package com.martige.service

import com.martige.model.MatchData
import com.martige.model.Stats
import io.ktor.client.*
import io.ktor.client.request.*
import model.DathostServerInfo
import model.Match
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class StatisticsService {

    suspend fun uploadStatistics(match: Match, gameServerId: String, client: HttpClient) {
        val matchId = match.id
        val serverListUrl = "https://dathost.net/api/0.1/game-servers"
        val serverList: List<DathostServerInfo> = client.get(serverListUrl)
        val map = serverList
            .filter { it.id == gameServerId }
            .map { it.csgo_settings?.mapgroup_start_map }
            .firstOrNull() ?: "Unknown Map"
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
                    data[mapName] = map
                }
            }
        }
    }

    fun getStatistics(steamId: String, mapName: String): List<Stats> {
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
            ).select { MatchData.steamId.eq(steamId).and(MatchData.mapName.like("%$mapName%")) }
                .groupBy(MatchData.steamId).map {
                    Stats(
                        it[MatchData.steamId],
                        it[MatchData.kills.sum()] ?: 0,
                        it[MatchData.deaths.sum()] ?: 0,
                        it[MatchData.assists.sum()] ?: 0,
                        (it[MatchData.kills.sum().castTo<Float>(FloatColumnType())
                            .div(MatchData.deaths.sum().castTo(FloatColumnType()))] ?: Float.MIN_VALUE),
                        mapName,
                    )
                }
        }

    }

    fun getTopTenPlayers(mapName: String): List<Stats> {
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType()))
            ).select { MatchData.mapName.like("%$mapName%") }
                .having { MatchData.kills.sum().greaterEq(100) }
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
                        mapName
                    )
                }
        }
    }

    fun getTopTenPlayersMonthRange(length: Int?, mapName: String): List<Stats>? {
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
            ).select {
                MatchData.createTime.greaterEq(pastTime)
                    .and(
                        MatchData.matchId.notLike("init%")
                            .and(MatchData.mapName.like("%$mapName%"))
                    )
            }
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
                        mapName
                    )
                }
        }
    }

    fun getMonthRangeStats(steamId: String, length: Int?, mapName: String): List<Stats>? {
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
                    .and(MatchData.mapName.like("%$mapName%"))
            }
                .groupBy(MatchData.steamId).map {
                    Stats(
                        it[MatchData.steamId],
                        it[MatchData.kills.sum()] ?: 0,
                        it[MatchData.deaths.sum()] ?: 0,
                        it[MatchData.assists.sum()] ?: 0,
                        (it[MatchData.kills.sum().castTo<Float>(FloatColumnType())
                            .div(MatchData.deaths.sum().castTo(FloatColumnType()))] ?: Float.MIN_VALUE),
                        mapName
                    )
                }
        }
    }

    fun getTopMaps(steamId: String): List<Stats> {
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.mapName,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType()))
            ).select { MatchData.steamId.eq(steamId).and(MatchData.mapName.notLike(" ")) }
                .groupBy(MatchData.steamId, MatchData.mapName)
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
                        it[MatchData.mapName],
                    )
                }
        }
    }

    fun getTopMapsRange(steamId: String, length: Int?): List<Stats>? {
        if (length == null) {
            return null
        }
        val pastTime = DateTime().minusMonths(length)
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.mapName,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum(),
                MatchData.kills.sum().castTo<Float>(FloatColumnType())
                    .div(MatchData.deaths.sum().castTo(FloatColumnType()))
            ).select { MatchData.steamId.eq(steamId).and(MatchData.createTime.greaterEq(pastTime)).and(MatchData.mapName.notLike(" ")) }
                .groupBy(MatchData.steamId, MatchData.mapName)
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
                        it[MatchData.mapName],
                    )
                }
        }
    }
}