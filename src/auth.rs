use axum::http;
use axum::http::{Request, StatusCode};
use axum::middleware::Next;
use axum::response::Response;
use std::env;

pub async fn auth<B>(req: Request<B>, next: Next<B>) -> Result<Response, StatusCode> {
    tracing::debug!("authenticating");
    let Ok(token) = env::var("AUTH_TOKEN") else {
        return Ok(next.run(req).await)
    };
    let auth_header = req
        .headers()
        .get(http::header::AUTHORIZATION)
        .and_then(|header| header.to_str().ok());

    return match auth_header {
        Some(auth_header) => {
            if token == auth_header.replace("TOKEN ", "") {
                Ok(next.run(req).await)
            } else {
                Err(StatusCode::UNAUTHORIZED)
            }
        }
        None => Err(StatusCode::UNAUTHORIZED),
    };
}
