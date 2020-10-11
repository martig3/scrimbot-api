package com.martige

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.http.*
import com.fasterxml.jackson.databind.*
import com.martige.service.UploadService
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.jackson.*
import io.ktor.util.*
import model.Match
import java.io.FileInputStream
import java.util.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(CORS) {
        method(HttpMethod.Post)
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
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
                UploadService().uploadDemo(match.id, match.game_server_id, client)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun loadProps(): Properties {
    val props = Properties()
    props.load(FileInputStream("application.properties"))
    return props
}

