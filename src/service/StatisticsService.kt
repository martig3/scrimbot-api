package com.martige.service

import com.martige.model.MatchData
import com.martige.model.Stats
import io.ktor.client.*
import io.ktor.client.request.*
import model.DathostServerInfo
import model.Match
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
            val playerTeamScore = when {
                match.team1_steam_ids.contains(it.steam_id) -> match.team1_stats.score
                else -> match.team2_stats.score
            }
            val enemyTeamScore = when (playerTeamScore) {
                match.team1_stats.score -> match.team2_stats.score
                else -> match.team1_stats.score
            }
            val matchResult = when {
                playerTeamScore > enemyTeamScore -> "W"
                playerTeamScore == enemyTeamScore -> "T"
                else -> "L"
            }
            transaction {
                MatchData.insert { data ->
                    data[steamId] = steamIdUpdated
                    data[MatchData.matchId] = matchId
                    data[MatchData.kills] = kills
                    data[MatchData.deaths] = deaths
                    data[MatchData.assists] = assists
                    data[createTime] = DateTime.now()
                    data[mapName] = map
                    data[MatchData.matchResult] = matchResult
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
                MatchData.adr.avg().castTo<Float>(FloatColumnType()),
                MatchData.hs.avg().castTo<Float>(FloatColumnType()),
                MatchData.rating.avg().castTo<Float>(FloatColumnType()),
                MatchData.rws.avg().castTo<Float>(FloatColumnType()),
                MatchData.matchId.count().castTo<Int>(IntegerColumnType()),
                Sum(
                    case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                    FloatColumnType()
                ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo<Float>(FloatColumnType())
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
                        it[MatchData.hs.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rws.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.adr.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rating.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.matchId.count().castTo(IntegerColumnType())] ?: 0,
                        it[Sum(
                            case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                            FloatColumnType()
                        ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo(FloatColumnType())]
                            ?: Float.MIN_VALUE
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
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
                MatchData.adr.avg().castTo<Float>(FloatColumnType()),
                MatchData.hs.avg().castTo<Float>(FloatColumnType()),
                MatchData.rating.avg().castTo<Float>(FloatColumnType()),
                MatchData.rws.avg().castTo<Float>(FloatColumnType()),
                MatchData.matchId.count().castTo<Int>(IntegerColumnType()),
                Sum(
                    case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                    FloatColumnType()
                ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo<Float>(FloatColumnType()),
            ).select { MatchData.mapName.like("%$mapName%") }
                .having { MatchData.kills.sum().greaterEq(100).and(MatchData.adr.sum().greater(Float.MIN_VALUE)) }
                .groupBy(MatchData.steamId)
                .orderBy(
                    MatchData.adr.avg().castTo<Float>(FloatColumnType()) to SortOrder.DESC
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
                        mapName,
                        it[MatchData.hs.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rws.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.adr.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rating.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.matchId.count().castTo(IntegerColumnType())] ?: 0,
                        it[Sum(
                            case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                            FloatColumnType()
                        ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo(FloatColumnType())]
                            ?: Float.MIN_VALUE
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
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
                MatchData.adr.avg().castTo<Float>(FloatColumnType()),
                MatchData.hs.avg().castTo<Float>(FloatColumnType()),
                MatchData.rating.avg().castTo<Float>(FloatColumnType()),
                MatchData.rws.avg().castTo<Float>(FloatColumnType()),
                MatchData.matchId.count().castTo<Int>(IntegerColumnType()),
                Sum(
                    case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                    FloatColumnType()
                ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo<Float>(FloatColumnType()),
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
                        mapName,
                        it[MatchData.hs.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rws.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.adr.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rating.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.matchId.count().castTo(IntegerColumnType())] ?: 0,
                        it[Sum(
                            case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                            FloatColumnType()
                        ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo(FloatColumnType())]
                            ?: Float.MIN_VALUE
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
                MatchData.adr.avg().castTo<Float>(FloatColumnType()),
                MatchData.hs.avg().castTo<Float>(FloatColumnType()),
                MatchData.rating.avg().castTo<Float>(FloatColumnType()),
                MatchData.rws.avg().castTo<Float>(FloatColumnType()),
                MatchData.matchId.count().castTo<Int>(IntegerColumnType()),
                Sum(
                    case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                    FloatColumnType()
                ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo<Float>(FloatColumnType()),
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
                        mapName,
                        it[MatchData.hs.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rws.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.adr.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rating.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.matchId.count().castTo(IntegerColumnType())] ?: 0,
                        it[Sum(
                            case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                            FloatColumnType()
                        ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo(FloatColumnType())]
                            ?: Float.MIN_VALUE,
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
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
                MatchData.adr.avg().castTo<Float>(FloatColumnType()),
                MatchData.hs.avg().castTo<Float>(FloatColumnType()),
                MatchData.rating.avg().castTo<Float>(FloatColumnType()),
                MatchData.rws.avg().castTo<Float>(FloatColumnType()),
                MatchData.matchId.count().castTo<Int>(IntegerColumnType()),
                Sum(
                    case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                    FloatColumnType()
                ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo<Float>(FloatColumnType()),
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
                        it[MatchData.hs.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rws.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.adr.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rating.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.matchId.count().castTo(IntegerColumnType())] ?: 0,
                        it[Sum(
                            case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                            FloatColumnType()
                        ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo(FloatColumnType())]
                            ?: Float.MIN_VALUE
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
                    .div(MatchData.deaths.sum().castTo(FloatColumnType())),
                MatchData.adr.avg().castTo<Float>(FloatColumnType()),
                MatchData.hs.avg().castTo<Float>(FloatColumnType()),
                MatchData.rating.avg().castTo<Float>(FloatColumnType()),
                MatchData.rws.avg().castTo<Float>(FloatColumnType()),
                MatchData.matchId.count().castTo<Int>(IntegerColumnType()),
                Sum(
                    case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                    FloatColumnType()
                ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo<Float>(FloatColumnType()),
            ).select {
                MatchData.steamId.eq(steamId).and(MatchData.createTime.greaterEq(pastTime))
                    .and(MatchData.mapName.notLike(" "))
            }
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
                        it[MatchData.hs.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rws.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.adr.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.rating.avg().castTo(FloatColumnType())] ?: Float.MIN_VALUE,
                        it[MatchData.matchId.count().castTo(IntegerColumnType())] ?: 0,
                        it[Sum(
                            case().When(MatchData.matchResult.eq("W"), intLiteral(1)).Else(intLiteral(0)),
                            FloatColumnType()
                        ).div(MatchData.matchId.count().castTo(FloatColumnType())).castTo(FloatColumnType())]
                            ?: Float.MIN_VALUE
                    )
                }
        }
    }
}
