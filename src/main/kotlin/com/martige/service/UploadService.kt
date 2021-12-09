package com.martige.service

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.MonthDay
import java.time.Year


class UploadService {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(UploadService::class.java)
        private var dropboxToken = System.getenv("DROPBOX_TOKEN")
        private var dropboxAppName = System.getenv("DROPBOX_APP_NAME")
        private var config: DbxRequestConfig = DbxRequestConfig.newBuilder("dropbox/$dropboxAppName").build()
        private lateinit var dropboxClient: DbxClientV2
    }

    fun uploadDemo(filename: String, gameServerId: String, map: String): String {
        val demoFileUrl = "https://dathost.net/api/0.1/game-servers/$gameServerId/files/$filename.dem"
        val uploadPath =
            "/${Year.now()}-${MonthDay.now().month.value}-${MonthDay.now().dayOfMonth}_pug_${map}_$filename.dem"
        log.info("Uploading $filename.dem...")
        dropboxClient = DbxClientV2(config, dropboxToken)
        GameServerService().getGameServerFile(demoFileUrl).use { response ->
            response.body?.byteStream().use { `in` ->
                dropboxClient.files().uploadBuilder(uploadPath)
                    .uploadAndFinish(`in`)
                log.info("Uploaded $uploadPath successfully")
            }
        }
        return dropboxClient.sharing().createSharedLinkWithSettings(uploadPath).url!!
    }
}
