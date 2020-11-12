package com.martige

import com.fasterxml.jackson.databind.SerializationFeature
import com.martige.model.Stats
import com.martige.service.StatisticsService
import com.martige.service.UploadService
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import model.DathostServerInfo
import model.Match
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.util.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CORS) {
        method(HttpMethod.Post)
        method(HttpMethod.Get)
        host("dathost.net", listOf("https"), emptyList())
        anyHost()
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    val auth64String = "Basic " + Base64.getEncoder()
        .encodeToString(
            "${System.getenv("dathost_username")}:${System.getenv("dathost_password")}"
                .toByteArray()
        )
    val jda = JDABuilder
        .create(
            System.getenv("discord_bot_token"),
            EnumSet.noneOf(GatewayIntent::class.java)
        )
        .setMemberCachePolicy(MemberCachePolicy.NONE)
        .disableCache(CacheFlag.ACTIVITY)
        .disableCache(CacheFlag.CLIENT_STATUS)
        .disableCache(CacheFlag.EMOTE)
        .disableCache(CacheFlag.VOICE_STATE)
        .disableCache(CacheFlag.MEMBER_OVERRIDES)
        .build()
    val client = HttpClient(Apache) {
        engine {
            followRedirects = true
        }
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
        defaultRequest {
            header("Authorization", auth64String)
            accept(ContentType.Application.Json)
        }
    }

    DatabaseFactory.init()

    routing {
        route("/api") {
            post("/match-end") {
                val match = call.receive<Match>()
                GlobalScope.launch {
                    UploadService().uploadDemo(match.id, match.game_server_id, client, jda)
                }
                StatisticsService().uploadStatistics(match, match.game_server_id, client)
                call.respond(HttpStatusCode.OK)
            }
            get("/stats") {
                val steamId: String = call.parameters["steamid"].toString()
                val length: Int? = call.parameters["length"]?.toIntOrNull()
                val mapName: String = call.parameters["map"] ?: ""
                val lengthParamExists: Boolean = call.parameters["length"]?.toIntOrNull() ?: -1 > 0
                val results: List<Stats>? = when (call.parameters["option"].toString() to lengthParamExists) {
                    "top10" to false -> StatisticsService().getTopTenPlayers(mapName)
                    "top10" to true -> StatisticsService().getTopTenPlayersMonthRange(length, mapName)
                    "range" to true -> StatisticsService().getMonthRangeStats(steamId, length, mapName)
                    "maps" to false -> StatisticsService().getTopMaps(steamId)
                    "maps" to true -> StatisticsService().getTopMapsRange(steamId, length)
                    else -> StatisticsService().getStatistics(steamId, mapName)
                }
                results?.let {
                    call.respond(it)
                    return@get
                }
                call.respond(HttpStatusCode.BadRequest)
            }
            route("/server") {
                get("/online") {
                    val serverId = call.parameters["serverid"]
                    val serverListUrl = "https://dathost.net/api/0.1/game-servers"
                    val serverList: List<DathostServerInfo> = client.get(serverListUrl)
                    val serverOn = serverList
                        .filter { it.id == serverId }
                        .map { it.on }
                        .firstOrNull()
                    serverOn?.let {
                        call.respond(it)
                        return@get
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

    }
}


