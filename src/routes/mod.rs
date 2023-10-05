mod ingest;
mod webhooks;

use crate::routes::ingest::ingest_routes;
use crate::routes::webhooks::webhook_routes;
use crate::AppState;
use axum::Router;

pub fn routes() -> Router<AppState> {
    Router::new()
        .nest("/ingest", ingest_routes())
        .nest("/webhooks", webhook_routes())
}
