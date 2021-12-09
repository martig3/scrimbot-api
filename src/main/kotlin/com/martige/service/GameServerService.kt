package com.martige.service

import com.martige.model.DathostServerInfo
import io.ktor.client.*
import io.ktor.client.request.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.*
import java.util.concurrent.TimeUnit

class GameServerService {
    val dathostAuth = "Basic " + Base64.getEncoder()
        .encodeToString(
            "${System.getenv("DATHOST_USERNAME")}:${System.getenv("DATHOST_PASSWORD")}"
                .toByteArray()
        )
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

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

    fun getGameServerFile(demoFileUrl: String): Response {
        val getFileRequest = Request.Builder()
            .url(demoFileUrl)
            .get()
            .header("Authorization", dathostAuth)
            .build()
        return httpClient.newCall(getFileRequest).execute()
    }

}
