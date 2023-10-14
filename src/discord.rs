use crate::models::ActionRow;
use axum::http;
use reqwest::{Client, Response, Result};
use serde_json::json;
use std::env;
use std::time::Duration;

const DISCORD_BASE_URL: &str = "https://discord.com/api";
#[derive(Clone)]
pub struct DiscordClient(Client);
impl DiscordClient {
    pub fn new() -> Result<Self> {
        let mut headers = http::HeaderMap::with_capacity(1);
        headers.insert(
            http::header::AUTHORIZATION,
            http::HeaderValue::from_str(&{
                let token = env::var("DISCORD_TOKEN").expect("DISCORD_TOKEN must be set");
                format!("Bot {}", token)
            })
            .unwrap(),
        );

        let client = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(10))
            .build()?;
        Ok(Self(client))
    }

    pub async fn send_msg(&self, content: &String, components: Vec<ActionRow>) -> Result<Response> {
        let channel_id = env::var("DISCORD_CHANNEL_ID").expect("DISCORD_CHANNEL_ID must be set");
        let body = json!({ "content": content, "components": components });
        Ok(self
            .0
            .post(format!(
                "{}/channels/{}/messages",
                DISCORD_BASE_URL, channel_id
            ))
            .json(&body)
            .send()
            .await?)
    }
}
