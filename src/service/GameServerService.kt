package com.martige.service

import io.ktor.client.*
import io.ktor.client.request.*
import model.DathostServerInfo
import java.util.*

class GameServerService {
    private val dathostAuth = "Basic " + Base64.getEncoder()
        .encodeToString(
            "${System.getenv("DATHOST_USERNAME")}:${System.getenv("DATHOST_PASSWORD")}"
                .toByteArray()
        )
    suspend fun getCurrentMap(client: HttpClient, gameServerId: String): String {
        val serverListUrl = "https://dathost.net/api/0.1/game-servers"
        val serverList: List<DathostServerInfo> = client.get(serverListUrl) {
            header("Authorization", dathostAuth)
        }
        return serverList
            .filter { it.id == gameServerId }
            .map { it.csgo_settings?.mapgroup_start_map }
            .firstOrNull() ?: "Unknown Map"
    }
}
