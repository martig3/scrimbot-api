package com.martige

import com.fasterxml.jackson.databind.SerializationFeature
import com.martige.model.Stats
import com.martige.service.GameServerService
import com.martige.service.MatchEndService
import com.martige.service.StatisticsService
import com.martige.service.UploadService
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
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
import kotlinx.coroutines.delay
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
fun Application.module() {
    val statsHostUrl = System.getenv("stats_host_url") ?: "127.0.0.1"
    install(CORS) {
        method(HttpMethod.Post)
        method(HttpMethod.Get)
        host("dathost.net")
        host(statsHostUrl)
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
    val loggingLevel = try {
        val logEnv = System.getenv("API_LOGGING_LEVEL")
        LogLevel.valueOf(logEnv)
    } catch (e: Exception) {
        LogLevel.INFO
    }
    val client = HttpClient(Apache) {
        engine {
            followRedirects = true
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = loggingLevel
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
                val delayParam: String? = call.parameters["delay"]
                val uploadParam: String? = call.parameters["upload"]
                val delay = if (delayParam?.toBooleanStrictOrNull() == null) true else delayParam.toBoolean()
                val upload = if (uploadParam?.toBooleanStrictOrNull() == null) true else uploadParam.toBoolean()
                GlobalScope.launch {
                    val map = GameServerService().getCurrentMap(client, match.game_server_id)
                    if (delay) {
                        log.info("Waiting for GOTV to finish...")
                        delay(140000)
                    }
                    var shareLink = "No file uploaded"
                    if (upload) {
                        shareLink = UploadService().uploadDemo(match.id, match.game_server_id, map)
                    }
                    MatchEndService().sendEOMMessage(match, jda, map, shareLink, client)
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


