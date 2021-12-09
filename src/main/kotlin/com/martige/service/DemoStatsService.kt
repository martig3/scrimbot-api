package com.martige.service

import com.martige.model.demostatsservice.DemoStatsResponse
import com.martige.model.demostatsservice.Player
import io.ktor.client.*
import io.ktor.client.request.*
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
