use derive_more::{AsRef, Deref, Display, From, Into};
use serde::{Deserialize, Serialize};
use serde_aux::prelude::*;
use sqlx::types::time::OffsetDateTime;
#[derive(Debug, Clone, From, Into, Deref, AsRef, Display, Serialize, Deserialize)]
#[repr(transparent)]
pub struct ServerId(pub(crate) String);
#[derive(Clone, Debug, From, Into, Deref, AsRef, Display, Serialize, Deserialize)]
#[repr(transparent)]
pub struct DathostMatchId(pub(crate) String);
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DathostMatchEnd {
    pub id: DathostMatchId,
    #[serde(rename = "game_server_id")]
    pub server_id: ServerId,
    pub team1: Team,
    pub team2: Team,
    pub players: Vec<Player>,
    pub settings: Settings,
    pub rounds_played: i32,
    pub finished: bool,
    pub cancel_reason: Option<String>,
}

#[derive(Default, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Team {
    pub name: String,
    pub stats: MatchStats,
}

#[derive(Default, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct MatchStats {
    pub score: i32,
}

#[derive(Default, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Player {
    pub match_id: String,
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub steam_id_64: i64,
    pub team: String,
    pub connected: bool,
    pub kicked: bool,
    pub stats: PlayerStats,
}

#[derive(Default, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PlayerStats {
    pub kills: i32,
    pub assists: i32,
    pub deaths: i32,
    pub mvps: i32,
    pub score: i32,
    #[serde(rename = "2ks")]
    pub n2ks: i32,
    #[serde(rename = "3ks")]
    pub n3ks: i32,
    #[serde(rename = "4ks")]
    pub n4ks: i32,
    #[serde(rename = "5ks")]
    pub n5ks: i32,
    pub kills_with_headshot: i32,
    pub kills_with_pistol: i32,
    pub kills_with_sniper: i32,
    pub damage_dealt: i32,
    pub entry_attempts: i32,
    pub entry_successes: i32,
    pub flashes_thrown: i32,
    pub flashes_successful: i32,
    pub flashes_enemies_blinded: i32,
    pub utility_thrown: i32,
    pub utility_damage: i32,
    #[serde(rename = "1vX_attempts")]
    pub n1v_x_attempts: i32,
    #[serde(rename = "1vX_wins")]
    pub n1v_x_wins: i32,
}

#[derive(Default, Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Settings {
    pub map: String,
    pub connect_time: i64,
    pub match_begin_countdown: i64,
}

#[derive(Debug, Clone)]
pub struct DathostMatch {
    pub id: i32,
    pub map: String,
    pub team1_score: i32,
    pub team2_score: i32,
    pub team1_name: String,
    pub team2_name: String,
    pub completed_at: OffsetDateTime,
}

#[derive(Deserialize)]
pub struct MatchEndParams {
    pub wait_for_gotv: Option<bool>,
}
