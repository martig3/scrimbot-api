package com.martige.service

import SteamWebAPIResponse
import io.ktor.client.*
import io.ktor.client.request.*
import model.Match
import model.PlayerStat
import net.dv8tion.jda.api.JDA

data class ScoreboardRow(val name: String?, val kills: Int, val assists: Int, val deaths: Int, val kpr: Float)
class MatchEndService {
    private var discordTextChannelId: Long = System.getenv("discord_textchannel_id").toLong()
    private var steamWebAPIKey: String = System.getenv("STEAM_WEB_API_KEY").toString()

    suspend fun sendEOMMessage(match: Match, jda: JDA, map: String, shareLink: String?, client: HttpClient) {
        val teamOneScore = match.team1_stats.score
        val teamTwoScore = match.team2_stats.score
        val roundsPlayed = match.rounds_played
        val steamNames = getSteamNames(match.team1_steam_ids + match.team2_steam_ids, client)
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
        stringBuilder.appendLine("**$teamOneScore - $teamTwoScore**   `$map`")
        stringBuilder.appendLine("```md")
        stringBuilder.appendLine("    Player              Kills   Assists   Deaths   KPR")
        stringBuilder.appendLine("-------------------------------------------------------")
        stringBuilder.appendLine("Team A")
        teamOneRows?.forEachIndexed { i, p ->
            stringBuilder.appendLine(formatRow(i + 1, p))
        }
        stringBuilder.appendLine("")
        stringBuilder.appendLine("Team B")
        teamTwoRows?.forEachIndexed { i, p ->
            stringBuilder.appendLine(formatRow(i + steamNames.size / 2, p))
        }
        stringBuilder.appendLine("```")
        stringBuilder.append("Download demo: $shareLink")
        channel?.sendMessage(stringBuilder.toString())?.queue()
    }

    private fun formatRow(i: Int, p: ScoreboardRow): String {
        val indexString = i.toString().padStart(2, ' ')
        val personaName = p.name?.padEnd(20, ' ')
        return "$indexString. $personaName${p.kills.toString().padEnd(3, ' ')}     " +
                p.assists.toString().padEnd(3, ' ') +
                "       ${p.deaths.toString().padEnd(3, ' ')}      ${String.format("%.2f", p.kpr)}"
    }

    private suspend fun getSteamNames(steamIds: List<String>, client: HttpClient): HashMap<String, String> {
        val steam64s = HashMap<String, String>()
        val steamNames = HashMap<String, String>()
        steamIds.forEach {
            val split = it.split(":")
            val v = 76561197960265728
            val y = split[1].toLong()
            val z = split[2].toLong()
            val steam64 = ((z * 2) + v + y).toString()
            steam64s[it] = steam64
        }
        val steam64String = steam64s.values.joinToString(",")
        val responseData: SteamWebAPIResponse =
            client.get("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002") {
                parameter("key", steamWebAPIKey)
                parameter("steamids", steam64String)
            }
        responseData.response.players.forEach {
            steamNames[steam64s.filterValues { v -> it.steamid == v }.keys.first()] = it.personaname
        }
        return steamNames
    }

    private fun getPlayer(
        playerStat: PlayerStat,
        steamNames: HashMap<String, String>,
        roundsPlayed: Int
    ): ScoreboardRow {
        val name = if (steamNames[playerStat.steam_id]?.length!! > 20) {
            steamNames[playerStat.steam_id]?.take(17) + "..."
        } else {
            steamNames[playerStat.steam_id]
        }
        val kpr = (playerStat.kills.toFloat() / roundsPlayed.toFloat())
        return ScoreboardRow(
            name,
            playerStat.kills,
            playerStat.assists,
            playerStat.deaths,
            kpr
        )
    }
}
