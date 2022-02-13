package com.martige.service

import com.martige.model.DatHostMatch
import net.dv8tion.jda.api.JDA
import kotlin.math.absoluteValue

data class ScoreboardRow(
    val steamId: String,
    val name: String,
    val kills: Int,
    val assists: Int,
    val deaths: Int,
    val adr: Double,
    val hsPercent: Double,
    val effFlashes: Int,
    val efpr: Double,
    val team: String,
)

class MatchEndService {
    private var discordTextChannelId: Long = System.getenv("DISCORD_TEXTCHANNEL_ID").toLong()

    fun sendEOMMessage(
        match: DatHostMatch,
        scoreboard: List<ScoreboardRow>,
        jda: JDA,
        map: String,
        shareLink: String?
    ) {
        val teamOneScore =  match.team1_stats.score
        val teamTwoScore = match.team2_stats.score
        val teamOneTeamString = scoreboard.first { match.team1_steam_ids.contains(it.steamId) }.team
        val teamOneRows = scoreboard
                .filter { it.team == teamOneTeamString}
                .sortedByDescending { it.adr }
        val teamTwoRows = scoreboard
                .filter { it.team != teamOneTeamString}
                .sortedByDescending { it.adr }

        val mvpAdr = scoreboard.maxOf { it.adr }
        val mvp = scoreboard.first { it.adr == mvpAdr }
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine("**$teamOneScore - $teamTwoScore**   `$map`")
        stringBuilder.appendLine("```md")
        stringBuilder.appendLine("    Player              K   A   D   ADR     HS%     EF   ")
        stringBuilder.appendLine("---------------------------------------------------------")
        stringBuilder.appendLine("Team A")
        teamOneRows.forEachIndexed { i, p ->
            stringBuilder.appendLine(formatRow(i + 1, p))
        }
        stringBuilder.appendLine("")
        stringBuilder.appendLine("Team B")
        teamTwoRows.forEachIndexed { i, p ->
            stringBuilder.appendLine(formatRow(i + 1, p))
        }
        stringBuilder.appendLine("```")
        stringBuilder.appendLine("Congrats to the MVP `${mvp.name}` with the highest ADR of " +
                "`${String.format("%.2f", mvp.adr.absoluteValue)}`!\n")
        stringBuilder.appendLine("Download demo: $shareLink")
        val channel = jda.getTextChannelById(discordTextChannelId)
        channel?.sendMessage(stringBuilder.toString())?.queue()
    }

    private fun formatRow(i: Int, p: ScoreboardRow): String {
        val indexString = i.toString().padStart(2, ' ')
        var personaName = p.name.replace("_", " ").trim()
        if (personaName.length > 18) personaName = personaName.substring(0, 18)
        personaName = personaName.padEnd(20, ' ')
        return "$indexString. $personaName" +
                p.kills.toString().padEnd(4, ' ') +
                p.assists.toString().padEnd(4, ' ') +
                p.deaths.toString().padEnd(4, ' ') +
                String.format("%.2f", p.adr.absoluteValue).padEnd(8, ' ') +
                String.format("%.2f", p.hsPercent.absoluteValue).padEnd(8, ' ') +
                p.effFlashes.toString().padEnd(8, ' ')
    }

}
