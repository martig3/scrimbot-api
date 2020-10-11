package model

data class Match(
    val cancel_reason: String?,
    val connect_time: Int?,
    val finished: Boolean?,
    val game_server_id: String,
    val id: String,
    val match_end_webhook_url: String?,
    val player_stats: List<PlayerStat>?,
    val round_end_webhook_url: String?,
    val rounds_played: Int?,
    val spectator_steam_ids: List<String>?,
    val team1_stats: Team1Stats?,
    val team1_steam_ids: List<String>?,
    val team2_stats: Team2Stats?,
    val team2_steam_ids: List<String>?,
    val warmup_time: Int?
)
