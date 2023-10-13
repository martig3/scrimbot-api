use crate::models::ServerId;
use crate::AppState;
use axum::extract::State;
use axum::headers::UserAgent;
use axum::response::IntoResponse;
use axum::routing::post;
use axum::{Router, TypedHeader};
use regex::Regex;
use reqwest::StatusCode;
use std::env;

pub fn ingest_routes() -> Router<AppState> {
    Router::new().route("/logs", post(post_logs))
}

pub async fn post_logs(
    TypedHeader(user_agent): TypedHeader<UserAgent>,
    state: State<AppState>,
    body: String,
) -> impl IntoResponse {
    if !user_agent.to_string().contains("Valve/Steam") {
        return StatusCode::UNAUTHORIZED;
    }
    let server_id = &ServerId(env::var("DATHOST_SERVER_ID").unwrap());
    let lines = body.split("\n");
    for line in lines {
        let said_regex = Regex::new(r#"say "(?<cmd>.+?)""#).unwrap();
        let Some(captures) = said_regex.captures(line) else {
            continue;
        };
        let cmd = &captures["cmd"];
        let send_result = match cmd {
            "!tech" => {
                state
                    .dathost
                    .send_console_msg(&server_id, "mp_pause_match".to_string())
                    .await
            }
            "!unpause" => {
                state
                    .dathost
                    .send_console_msg(&server_id, "mp_unpause_match".to_string())
                    .await
            }
            _ => continue,
        };
        let Ok(status_code) = send_result else {
            return StatusCode::INTERNAL_SERVER_ERROR;
        };

        if cmd == "!tech" {
            if let Err(s) = state.dathost.send_console_msg(&server_id, "say Either team can type !unpause to immediately resume the match. Be sure both teams are ready before unpausing.".to_string()).await {
                tracing::error!("{:#?}", s);
            }
        }

        if status_code.as_u16() != 200 {
            tracing::error!("send console msg status: {}", status_code)
        }
    }
    StatusCode::OK
}
