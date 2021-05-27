package com.martige.service

import io.ktor.client.*
import io.ktor.client.request.*
import model.DathostServerInfo

class GameServerService {
    suspend fun getCurrentMap(client: HttpClient, gameServerId: String): String {
        val serverListUrl = "https://dathost.net/api/0.1/game-servers"
        val serverList: List<DathostServerInfo> = client.get(serverListUrl)
        return serverList
            .filter { it.id == gameServerId }
            .map { it.csgo_settings?.mapgroup_start_map }
            .firstOrNull() ?: "Unknown Map"
    }
}
