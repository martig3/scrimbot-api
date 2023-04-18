package com.martige.service

import com.martige.model.demostatsservice.DemoStatsResponse
import com.martige.model.demostatsservice.Player
import io.ktor.client.*
import io.ktor.client.call.*
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
        val demoFileUrl = "https://dathost.net/api/0.1/game-servers/$gameServerId/files/$matchId.dem"
        GameServerService().getGameServerFile(demoFileUrl).use { response ->
            response.body?.byteStream().use { `in` ->
                val stats: DemoStatsResponse = client.post(demoStatsUrl) {
                    setBody(`in`)
                    header("Authorization", demoStatsAuth)
                }.body()
                return stats.players
            }
        }
    }

}
