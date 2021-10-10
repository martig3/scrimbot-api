package com.martige

import com.fasterxml.jackson.databind.SerializationFeature
import com.martige.service.*
import io.ktor.application.*
import io.ktor.auth.*
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import model.DatHostMatch
import model.Stats
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.util.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

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

    val authUser = System.getenv("STATS_USER").trim()
    val authPass = System.getenv("STATS_PASSWORD").trim()
    if (authUser == "" || authPass == "") log.error("'STATS_USER' and/or 'STATS_PASSWORD' env variables are not set!")
    install(Authentication) {
        basic {
            realm = "Access to the '/api' path"
            validate { credentials ->
                if (credentials.name == authUser && credentials.password == authPass) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
    val jda = JDABuilder
        .create(
            System.getenv("DISCORD_BOT_TOKEN"),
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
        LogLevel.NONE
    }
    val client = HttpClient(Apache) {
        engine {
            followRedirects = true
            socketTimeout = 60000
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = loggingLevel
        }
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
        defaultRequest {
            accept(ContentType.Application.Json)
        }
    }

    DatabaseFactory.init()

    routing {
        route("/api") {
            authenticate {
                post("/match-end") {
                    val match = call.receive<DatHostMatch>()
                    if (match.player_stats == null || match.player_stats.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val delayParam: String? = call.parameters["delay"]
                    val uploadParam: String? = call.parameters["upload"]
                    val delay = if (delayParam?.toBooleanStrictOrNull() == null) true else delayParam.toBoolean()
                    val upload = if (uploadParam?.toBooleanStrictOrNull() == null) true else uploadParam.toBoolean()
                    val map = async { GameServerService().getCurrentMap(client, match.game_server_id) }
                    if (delay) {
                        log.info("Waiting for GOTV to finish...")
                        delay(140000)
                    }
                    val shareLink = async {
                        var link = "No file uploaded"
                        if (upload) {
                            link = UploadService().uploadDemo(match.id, match.game_server_id, map.await())
                        }
                        return@async link
                    }
                    val demoStats = async {
                        log.info("Retrieving detailed stats from demo-stats-service...")
                        return@async DemoStatsService().getDemoStats(client, match.game_server_id, match.id)
                    }
                    val steamNames = async {
                        log.info("Retrieving persona names from steam api...")
                        return@async SteamWebService().getSteamNames(
                            match.team1_steam_ids + match.team2_steam_ids, client
                        )
                    }
                    val scoreboard = async {
                        return@async StatisticsService().createScoreboard(
                            match.player_stats,
                            demoStats.await(),
                            steamNames.await()
                        )
                    }
                    val scoreboardRows = scoreboard.await()
                    MatchEndService().sendEOMMessage(match, scoreboardRows, jda, map.await(), shareLink.await())
                    StatisticsService().uploadStatistics(match, scoreboardRows, match.game_server_id, client)
                    call.respond(HttpStatusCode.OK)
                }
                get("/stats") {
                    val steamId: String = call.parameters["steamid"].toString()
                    val length: Int? = call.parameters["length"]?.toIntOrNull()
                    val mapName: String = call.parameters["map"] ?: ""
                    val mapCountLimit: Int = call.parameters["mapCountLimit"]?.toIntOrNull() ?: 0
                    val lengthParamExists: Boolean = (call.parameters["length"]?.toIntOrNull() ?: -1) > 0
                    val results: List<Stats>? = when (call.parameters["option"].toString() to lengthParamExists) {
                        "top10" to false -> StatisticsService().getTopTenPlayers(mapName, mapCountLimit)
                        "top10" to true -> StatisticsService().getTopTenPlayersMonthRange(length, mapName, mapCountLimit)
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
            }
        }
    }
}


