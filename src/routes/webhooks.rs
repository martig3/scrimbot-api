use crate::errors::Error;
use crate::models::DatHostMatch;
use crate::AppState;
use axum::extract::State;
use axum::response::IntoResponse;
use axum::routing::post;
use axum::{Json, Router};
use reqwest::StatusCode;
use std::env;
use std::time::Duration;
use tokio::time::sleep;

pub fn webhook_routes() -> Router<AppState> {
    Router::new().route("/match-end", post(match_end))
}

pub async fn match_end(
    state: State<AppState>,
    dathost_match: Json<DatHostMatch>,
) -> impl IntoResponse {
    let tv_delay = env::var("TV_DELAY").unwrap_or("105".to_string());
    sleep(Duration::from_secs(tv_delay.parse::<u64>().unwrap() + 30)).await;
    let stop_status = state.dathost.stop_server(&dathost_match.server_id).await?;
    if stop_status.as_u16() != 500 {
        return Err(Error::StopServerError);
    }
    let path = format!("{}.dem", dathost_match.id);
    let demo = state
        .dathost
        .get_file(&dathost_match.server_id, &path)
        .await?;

    if state.bucket.put_object(path, &demo).await?.status_code() != 200 {
        return Err(Error::DemoUploadError);
    }
    Ok(StatusCode::OK)
}
