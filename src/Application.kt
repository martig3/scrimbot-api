package com.martige

import com.fasterxml.jackson.databind.SerializationFeature
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
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import model.DathostServerInfo
import model.Match
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port) {
        install(CORS) {
            method(HttpMethod.Post)
            method(HttpMethod.Get)
            host("dathost.net", listOf("https"), emptyList())
        }

        install(ContentNegotiation) {
            jackson {
                enable(SerializationFeature.INDENT_OUTPUT)
            }
        }
        val auth64String = "Basic " + Base64.getEncoder()
            .encodeToString(
                "${UploadService.props.getProperty("dathost.username")}:${UploadService.props.getProperty("dathost.password")}"
                    .toByteArray()
            )
        val jda = JDABuilder
            .create(
                UploadService.props.getProperty("discord.bot.token"),
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
        routing {
            route("/api") {
                post("/match-end") {
                    val match = call.receive<Match>()
                    UploadService().uploadDemo(match.id, match.game_server_id, client, jda)
                    call.respond(HttpStatusCode.OK)
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
                        serverOn?.let { call.respond(it) }
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }

        }
    }.start(wait = true)
}

fun loadProps(): Properties {
    val props = Properties()
    props.load(FileInputStream("application.properties"))
    return props
}

