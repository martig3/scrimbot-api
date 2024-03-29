# scrimbot-api

Microservice for processing CSGO end of match payloads and statistics queries.

## Features

- End of match printout to Discord text channel
- Handle automating technical timeouts in matches
- Upload demo files to S3 compatible API 
- Save match statistics to database

### End of Match Printout Example

![preview](https://i.imgur.com/mYhfN9D.png)

## Currently supported webhooks

- Dathost Match-API

## Setup

Extract release .zip and navigate to the `/bin` directory and run your platform's appropriate executable with the following env variables:

### Environment Variables

```dotenv
ENV="dev || prd"
PORT="defaults to 3000"
DATHOST_USER=
DATHOST_PASSWORD=
DATABASE_URL=postgres://postgres:postgres@localhost/scrimbot
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
BUCKET_NAME=
AWS_ENDPOINT=
DATHOST_SERVER_ID=
BUCKET_BASE_URL=
STEAM_KEY=
DISCORD_TOKEN=
DISCORD_CHANNEL_ID=
AUTH_TOKEN=
```

## Roadmap

- Re-write Statistics API
