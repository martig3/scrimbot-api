mod ingest;
mod webhooks;

use crate::auth::auth;
use crate::routes::ingest::ingest_routes;
use crate::routes::webhooks::webhook_routes;
use crate::AppState;
use axum::{middleware, Router};

pub fn routes() -> Router<AppState> {
    Router::new()
        .nest("/webhooks", webhook_routes())
        .layer(middleware::from_fn(auth))
        .nest("/ingest", ingest_routes())
}
