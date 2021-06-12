package com.martige.service

import model.SteamWebAPIResponse
import io.ktor.client.*
import io.ktor.client.request.*

class SteamWebService {

    private var steamWebAPIKey: String = System.getenv("STEAM_WEB_API_KEY").toString()

    suspend fun getSteamNames(steamIds: List<String>, client: HttpClient): HashMap<String, String> {
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
}
