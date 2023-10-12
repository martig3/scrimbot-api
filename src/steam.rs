use crate::models::{PlayerSummariesResponse, SteamUser};
use reqwest::{Client, Result};
use std::env;
use std::time::Duration;

const STEAM_BASE_URL: &str = "https://api.steampowered.com";

#[derive(Clone)]
pub struct SteamClient(Client);
impl SteamClient {
    pub fn new() -> Result<Self> {
        let client = Client::builder().timeout(Duration::from_secs(10)).build()?;
        Ok(Self(client))
    }

    pub async fn get_player_summaries(&self, steam_ids: Vec<u64>) -> Result<Vec<SteamUser>> {
        let steam_key = env::var("STEAM_KEY").expect("STEAM_KEY must be set");
        let steam_ids: String = steam_ids
            .into_iter()
            .map(|id| format!("{},", id))
            .reduce(|mut acc, id| {
                acc.push_str(id.as_str());
                acc
            })
            .unwrap();
        let resp = self
            .0
            .post(format!(
                "{}/ISteamUser/GetPlayerSummaries/v0002",
                STEAM_BASE_URL
            ))
            .query(&[["key", &steam_key], ["steamids", &steam_ids]])
            .send()
            .await?
            .json::<PlayerSummariesResponse>()
            .await?;
        Ok(resp.response.players)
    }
}
