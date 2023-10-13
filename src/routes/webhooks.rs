use crate::db::{create_match, create_match_stats};
use crate::errors::Error;
use crate::models::{ActionRow, DathostMatchEnd, MatchEndParams, MessageComponent};
use crate::utils::end_of_match_msg;
use crate::AppState;
use axum::extract::{Query, State};
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
    query: Query<MatchEndParams>,
    dathost_match: Json<DathostMatchEnd>,
) -> impl IntoResponse {
    if let Some(reason) = &dathost_match.cancel_reason {
        tracing::info!("cancel reason: {}", reason);
        return Ok(StatusCode::OK);
    }
    let created_match = create_match(&state.db, &dathost_match.0).await?;
    create_match_stats(&state.db, &dathost_match.0, created_match.id).await?;
    let delay = match query.wait_for_gotv {
        Some(b) => b,
        None => true,
    };
    if delay {
        let tv_delay = env::var("TV_DELAY")
            .unwrap_or("105".to_string())
            .parse::<u64>()
            .unwrap()
            + 30;
        tracing::info!("sleeping for {} sec", tv_delay);
        sleep(Duration::from_secs(tv_delay)).await;
    }
    let path = format!("{}.dem", dathost_match.id);
    tracing::info!("fetching demo file '{}'", path);
    let demo = state
        .dathost
        .get_file(&dathost_match.server_id, &path)
        .await?;
    tracing::info!("uploading demo to s3");
    let s3_status = state.bucket.put_object(&path, &demo).await?.status_code();
    if s3_status != 200 {
        tracing::error!("s3 error: {}", s3_status);
        return Err(Error::DemoUploadError);
    }
    let eom = end_of_match_msg(&state.steam, &dathost_match.0).await?;
    let bucket_base_url = env::var("BUCKET_BASE_URL").expect("BUCKET_BASE_URL must be set");
    let components = vec![ActionRow {
        component_type: 1,
        components: vec![MessageComponent {
            component_type: 2,
            label: "Download Demo".to_string(),
            style: 5,
            custom_id: None,
            url: Some(format!("{}/{}", bucket_base_url, &path)),
        }],
    }];
    tracing::info!("sending end of match message");
    let discord_resp = state.discord.send_msg(&eom, components).await?;
    if discord_resp.status() != 200 {
        tracing::error!("discord error resp: {}", discord_resp.text().await?);
    }
    Ok(StatusCode::OK)
}
