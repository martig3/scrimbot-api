package com.martige.service

import com.martige.model.MatchData
import com.martige.model.Stats
import model.Match
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class StatisticsService {


    fun uploadStatistics(match: Match) {
        val matchId = match.id
        match.player_stats?.forEach {
            val steamIdUpdated = it.steam_id.replaceRange(6, 7, "1")
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

    fun getStatistics(steamId: String): Stats? {
        return transaction {
            MatchData.slice(
                MatchData.steamId,
                MatchData.kills.sum(),
                MatchData.deaths.sum(),
                MatchData.assists.sum()
            ).select { MatchData.steamId.eq(steamId) }.groupBy(MatchData.steamId).map {
                Stats(
                    it[MatchData.steamId],
                    it[MatchData.kills.sum()] ?: 0,
                    it[MatchData.deaths.sum()] ?: 0,
                    it[MatchData.assists.sum()] ?: 0
                )
            }.firstOrNull()
        }

    }
}