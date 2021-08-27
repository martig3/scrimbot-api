package com.martige.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.content.*
import io.ktor.content.ByteArrayContent
import io.ktor.http.*
import io.ktor.http.content.*
import model.demostatsservice.DemoStatsResponse
import model.demostatsservice.Player
import java.io.File
import java.util.*

class DemoStatsService {
    private var demoStatsUrl: String = System.getenv("DEMO_STATS_URL").toString()
    private val demoStatsAuth = "Basic " + Base64.getEncoder()
        .encodeToString(
            "${System.getenv("DEMO_STATS_USER")}:${System.getenv("DEMO_STATS_PASSWORD")}"
                .toByteArray()
        )

    suspend fun getDemoStats(client: HttpClient, gameServerId: String, matchId: String): List<Player> {
        val stats: DemoStatsResponse = client.get(demoStatsUrl) {
            parameter("url", "https://dathost.net/api/0.1/game-servers/$gameServerId/files/$matchId.dem")
            parameter("auth", GameServerService().dathostAuth)
            header("Authorization", demoStatsAuth)
        }
        return stats.players
    }

}
