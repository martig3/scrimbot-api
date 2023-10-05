use axum::http;
use base64::engine::general_purpose;
use base64::Engine;
use std::{env, time::Duration};

use bytes::Bytes;
use reqwest::{Client, Result, StatusCode};

use crate::models::ServerId;

#[derive(Clone)]
pub struct DathostClient(Client);

impl DathostClient {
    pub fn new() -> Result<Self> {
        let mut headers = http::HeaderMap::with_capacity(1);
        headers.insert(
            http::header::AUTHORIZATION,
            http::HeaderValue::from_str(&{
                let username = env::var("DATHOST_USER").expect("DATHOST_USER must be set");
                let password = env::var("DATHOST_PASSWORD").ok();
                format!(
                    "Basic {}",
                    general_purpose::STANDARD.encode(format!(
                        "{username}:{password}",
                        password = password.as_deref().unwrap_or("")
                    ))
                )
            })
            .unwrap(),
        );

        let client = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_secs(60 * 10))
            .build()?;
        Ok(Self(client))
    }

    pub async fn get_file(&self, server_id: &ServerId, path: &str) -> Result<Bytes> {
        self.0
            .get(&format!(
                "https://dathost.net/api/0.1/game-servers/{server_id}/files/{path}"
            ))
            .send()
            .await?
            .bytes()
            .await
    }
    pub async fn send_console_msg(&self, server_id: &ServerId, msg: String) -> Result<StatusCode> {
        Ok(self
            .0
            .post(&format!(
                "https://dathost.net/api/0.1/game-servers/{server_id}/console"
            ))
            .form(&[("line", &msg)])
            .send()
            .await?
            .status())
    }
    pub async fn stop_server(&self, server_id: &ServerId) -> Result<StatusCode> {
        Ok(self
            .0
            .post(&format!(
                "https://dathost.net/api/0.1/game-servers/{server_id}/stop"
            ))
            .send()
            .await?
            .status())
    }
}
