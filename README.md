# scrimbot-api

Microservice for processing end of match payloads and statistics queries.

## Features

- End of match printout to Discord text channel
- Upload demo files to Dropbox
- Save match statistics in database
- Statistics API

### End of Match Printout Example

![preview](https://i.imgur.com/XuPQt5o.jpg)

## Currently supported webhooks

- Dathost Match-API

## Setup

Until releases are available, clone & `./gradlew run`

### Environment Variables

|Env Variable|Description|
|---|---|
|DISCORD_BOT_TOKEN| Your app's discord bot token|
|DISCORD_TEXTCHANNEL_ID| Id of text channel end of match message gets sent to|
|STATS_USER| Username for Scrimbot-API auth|
|STATS_PASSWORD| Username for Scrimbot-API auth|
|DB_URL| Postgres DB URL|
|DB_USER| DB Username|
|DB_PASSWORD| DB Password|
|STEAM_WEB_API_KEY| Your Steam Web API key|
|DROPBOX_TOKEN| Your discord api token|
|DATHOST_USERNAME| Your dathost account username|
|DATHOST_PASSWORD| Your dathost account password|
|API_LOGGING_LEVEL| Optional logging level, defaults to `none`|

