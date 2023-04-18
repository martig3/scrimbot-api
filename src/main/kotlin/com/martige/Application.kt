package com.martige

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.martige.model.DatHostMatch
import com.martige.model.Stats
import com.martige.service.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.event.Level
import java.time.Duration
import java.util.*


fun main() {
    val port: Int = System.getenv("PORT").takeUnless { it.isNullOrEmpty() }?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::applicationModule).start(wait = true)
}

fun Application.module() {
    val statsHostUrl = System.getenv("stats_host_url") ?: "127.0.0.1"
    install(CORS) {
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHost("dathost.net")
        allowHost(statsHostUrl)
    }

    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }

    install(CallLogging) {
        level = Level.INFO
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
        .disableCache(CacheFlag.VOICE_STATE)
        .disableCache(CacheFlag.MEMBER_OVERRIDES)
        .build()
    val client = HttpClient(OkHttp) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        engine {
            config {
                followRedirects(true)
                callTimeout(Duration.ofSeconds(6000))
            }
        }

    }

    DatabaseFactory.init()

    routing {
        route("/api") {
            authenticate {
                post("/match-end") {
                    val match = call.receive<DatHostMatch>()
                    if (match.player_stats.isNullOrEmpty()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    val delayParam: String? = call.parameters["delay"]
                    val uploadParam: String? = call.parameters["upload"]
                    val delay = if (delayParam?.toBooleanStrictOrNull() == null) true else delayParam.toBoolean()
                    val upload = if (uploadParam?.toBooleanStrictOrNull() == null) true else uploadParam.toBoolean()
                    val map = async { GameServerService().getCurrentMap(client, match.game_server_id) }
                    if (delay) {
                        call.application.environment.log.info("Waiting for GOTV to finish...")
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
                        call.application.environment.log.info("Retrieving detailed stats from csgo-demo-stats api...")
                        return@async DemoStatsService().getDemoStats(client, match.game_server_id, match.id)
                    }
                    val scoreboard = async {
                        return@async StatisticsService().createScoreboard(
                            demoStats.await(),
                        )
                    }
                    val scoreboardRows = scoreboard.await()
                    call.application.environment.log.info("Sending end of match message...")
                    MatchEndService().sendEOMMessage(match, scoreboardRows, jda, map.await(), shareLink.await())
                    call.application.environment.log.info("Uploading stats...")
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
                        "top10" to true -> StatisticsService().getTopTenPlayersMonthRange(
                            length,
                            mapName,
                            mapCountLimit
                        )

                        "range" to true -> StatisticsService().getMonthRangeStats(steamId, length, mapName)
                        "maps" to false -> StatisticsService().getTopMaps(steamId)
                        "maps" to true -> StatisticsService().getTopMapsRange(steamId, length)
                        "players" to false -> StatisticsService().getPlayerStats(
                            mapName,
                            steamIds = call.parameters["steamids"].toString().split(",")
                        )

                        "players" to true -> StatisticsService().getPlayerStatsMonthsRange(
                            mapName,
                            steamIds = call.parameters["steamids"].toString().split(","),
                            length
                        )

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

fun Application.applicationModule() {
    module()
}





