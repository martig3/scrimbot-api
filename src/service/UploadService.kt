package com.martige.service

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.martige.loadProps
import io.ktor.client.*
import io.ktor.client.request.*
import model.DathostServerInfo
import net.dv8tion.jda.api.JDA
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.time.MonthDay
import java.time.Year


class UploadService {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(UploadService::class.java)
        var props = loadProps()
        private var dropboxToken = props.getProperty("dropbox.token")
        private var dropboxAppName = props.getProperty("dropbox.app.name")
        private var discordTextChannelId: Long = props.getProperty("discord.textchannel.id").toLong()
        private var config: DbxRequestConfig = DbxRequestConfig.newBuilder("dropbox/$dropboxAppName").build()
        private lateinit var dropboxClient: DbxClientV2
    }

    suspend fun uploadDemo(filename: String, gameServerId: String, client: HttpClient, jda: JDA) {
        val demoFileUrl = "https://dathost.net/api/0.1/game-servers/$gameServerId/files/$filename.dem"
        val serverListUrl = "https://dathost.net/api/0.1/game-servers"
        val serverList: List<DathostServerInfo> = client.get(serverListUrl)
        val demoFile: ByteArray = client.get(demoFileUrl)
        val map = serverList
            .filter { it.id == gameServerId }
            .map { it.csgo_settings?.mapgroup_start_map }
            .firstOrNull() ?: "Unknown Map"
        dropboxClient = DbxClientV2(config, dropboxToken)
        val uploadPath =
            "/${Year.now()}-${MonthDay.now().month.value}-${MonthDay.now().dayOfMonth}_pug_${map}_$filename.dem"
        dropboxClient.files().uploadBuilder(uploadPath).uploadAndFinish(ByteArrayInputStream(demoFile))
        val shareLink = dropboxClient.sharing().createSharedLinkWithSettings(uploadPath)
        val channel = jda.getTextChannelById(discordTextChannelId)
        channel?.sendMessage("New `.dem` replay files are available: \n$map - ${shareLink.url}")?.queue()
    }
}
