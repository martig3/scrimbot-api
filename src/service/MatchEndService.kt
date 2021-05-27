package com.martige.service

import model.Match
import model.PlayerStat
import net.dv8tion.jda.api.JDA

data class ScoreboardRow(val name: String?, val kills: Int, val assists: Int, val deaths: Int, val impact: Float)
class MatchEndService {
    private var discordTextChannelId: Long = System.getenv("discord_textchannel_id").toLong()

    fun sendEOMMessage(match: Match, jda: JDA, map: String, shareLink: String?) {
        val teamOneScore = match.team1_stats.score
        val teamTwoScore = match.team2_stats.score
        val roundsPlayed = teamOneScore + teamTwoScore
        val steamNames = getSteamNames(match.team1_steam_ids + match.team2_steam_ids)
        val teamOneRows =
            match.player_stats
                ?.filter { match.team1_steam_ids.contains(it.steam_id) }
                ?.map { getPlayer(it, steamNames, roundsPlayed) }
                ?.sortedByDescending { it.kills }
        val teamTwoRows = match.player_stats
            ?.filter { match.team2_steam_ids.contains(it.steam_id) }
            ?.map { getPlayer(it, steamNames, roundsPlayed) }
            ?.sortedByDescending { it.kills }
        val channel = jda.getTextChannelById(discordTextChannelId)
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("$map $teamOneScore - $teamTwoScore")
        stringBuilder.appendLine("```md")
        stringBuilder.appendLine("   Player   Kills   Assists   Deaths   Impact")
        teamOneRows?.forEachIndexed { i, p ->
            stringBuilder.appendLine("$i. ${p.name} - ${p.kills}   ${p.assists}   ${p.deaths}   ${p.impact}")
        }
        teamTwoRows?.forEachIndexed { i, p ->
            stringBuilder.appendLine("$i. ${p.name} - ${p.kills}   ${p.assists}   ${p.deaths}   ${p.impact}")
        }
        stringBuilder.appendLine("```")
        stringBuilder.append("Download demo: $shareLink")
        channel?.sendMessage(stringBuilder.toString())?.queue()
    }

    private fun getSteamNames(steamIds: List<String>): HashMap<String, String> {
        return HashMap()
    }

    fun getPlayer(playerStat: PlayerStat, steamNames: HashMap<String, String>, roundsPlayed: Int): ScoreboardRow {
        val kpr = (playerStat.kills / roundsPlayed).toFloat()
        val apr = (playerStat.assists / roundsPlayed).toFloat()
        val impact = ((2.13 * kpr) + (0.42 * apr) - 0.41).toFloat()
        return ScoreboardRow(
            steamNames[playerStat.steam_id],
            playerStat.kills,
            playerStat.assists,
            playerStat.deaths,
            impact
        )
    }
}
