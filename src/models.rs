use derive_more::{AsRef, Deref, Display, From, Into};
use serde::{Deserialize, Serialize};
#[derive(Debug, From, Into, Deref, AsRef, Display, Serialize, Deserialize)]
#[repr(transparent)]
pub struct ServerId(pub(crate) String);
#[derive(Debug, Deserialize, Serialize)]
pub struct DatHostMatch {
    pub id: String,
    #[serde(rename = "game_server_id")]
    pub server_id: ServerId,
    pub match_series_id: Option<String>,
    pub map: Option<String>,
    pub finished: bool,
}
