use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde_json::json;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(transparent)]
    S3(#[from] s3::error::S3Error),
    #[error(transparent)]
    Reqwest(#[from] reqwest::Error),
    #[error(transparent)]
    Sqlx(#[from] sqlx::Error),
    #[error("failed to upload demo to S3")]
    DemoUploadError,
    #[error("failed to stop dathost server")]
    StopServerError,
}

impl IntoResponse for Error {
    fn into_response(self) -> Response {
        let json = json!({
            "error": self.to_string(),
        });
        (StatusCode::INTERNAL_SERVER_ERROR, json.to_string()).into_response()
    }
}
