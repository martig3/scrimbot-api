use crate::db::{create_match, create_match_stats};
use crate::errors::Error;
use crate::models::{DathostMatchEnd, MatchEndParams};
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
        println!("cancel reason: {}", reason);
        return Ok(StatusCode::OK);
    }
    let created_match = create_match(&state.db, &dathost_match.0).await?;
    create_match_stats(&state.db, &dathost_match.0, created_match.id).await?;
    let delay = match query.wait_for_gotv {
        Some(b) => b,
        None => false,
    };
    if delay {
        let tv_delay = env::var("TV_DELAY")
            .unwrap_or("105".to_string())
            .parse::<u64>()
            .unwrap()
            + 30;
        println!("sleeping for {} sec", tv_delay);
        sleep(Duration::from_secs(tv_delay)).await;
    }
    let stop_status = state.dathost.stop_server(&dathost_match.server_id).await?;
    if stop_status.as_u16() != 200 {
        return Err(Error::StopServerError);
    }
    let path = format!("{}.dem", dathost_match.id);
    println!("fetching demo file '{}'", path);
    let demo = state
        .dathost
        .get_file(&dathost_match.server_id, &path)
        .await?;
    println!("uploading demo to s3");
    let s3_status = state.bucket.put_object(path, &demo).await?.status_code();
    if s3_status != 200 {
        eprintln!("s3 error: {}", s3_status);
        return Err(Error::DemoUploadError);
    }
    Ok(StatusCode::OK)
}
