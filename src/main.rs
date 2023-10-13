mod auth;
mod dathost;
mod db;
mod discord;
mod errors;
pub mod models;
pub mod routes;
mod steam;
mod utils;

use axum::body::boxed;
use axum::http::{header, HeaderValue, Method};
use axum::{body::Bytes, Router};
use std::env;

use crate::dathost::DathostClient;
use crate::discord::DiscordClient;
use crate::routes::routes;
use crate::steam::SteamClient;
use dotenvy::dotenv;
use s3::creds::Credentials;
use s3::{Bucket, Region};
use sqlx::migrate::Migrator;
use sqlx::PgPool;
use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;
use std::time::Duration;
use tower::ServiceBuilder;
use tower_http::cors::CorsLayer;
use tower_http::{
    timeout::TimeoutLayer,
    trace::{DefaultMakeSpan, DefaultOnResponse, TraceLayer},
    LatencyUnit, ServiceBuilderExt,
};

static MIGRATOR: Migrator = sqlx::migrate!();
#[derive(Clone)]
pub struct AppState {
    db: PgPool,
    dathost: DathostClient,
    bucket: Bucket,
    discord: DiscordClient,
    steam: SteamClient,
}

#[tokio::main]
async fn main() {
    dotenv().ok();
    tracing_subscriber::fmt()
        .with_max_level(
            tracing::Level::from_str(
                env::var("RUST_LOG")
                    .unwrap_or_else(|_| "INFO".to_string())
                    .as_str(),
            )
            .unwrap(),
        )
        .init();

    let sensitive_headers: Arc<[_]> = vec![header::AUTHORIZATION, header::COOKIE].into();
    let cors = CorsLayer::new()
        .allow_headers(vec![
            header::ACCEPT,
            header::ACCEPT_LANGUAGE,
            header::AUTHORIZATION,
            header::CONTENT_LANGUAGE,
            header::CONTENT_TYPE,
            header::VARY,
        ])
        .allow_methods(vec![
            Method::GET,
            Method::POST,
            Method::PUT,
            Method::DELETE,
            Method::HEAD,
            Method::OPTIONS,
            Method::CONNECT,
            Method::PATCH,
            Method::TRACE,
        ]);
    let middleware = ServiceBuilder::new()
        .sensitive_request_headers(sensitive_headers.clone())
        .layer(
            TraceLayer::new_for_http()
                .on_body_chunk(|chunk: &Bytes, latency: Duration, _: &tracing::Span| {
                    tracing::trace!(size_bytes = chunk.len(), latency = ?latency, "sending body chunk")
                })
                .make_span_with(DefaultMakeSpan::new().include_headers(true))
                .on_response(DefaultOnResponse::new().include_headers(true).latency_unit(LatencyUnit::Micros))
        )
        .layer(TimeoutLayer::new(Duration::from_secs(3 * 60)))
        .layer(cors)
        // Box the response body so it implements `Default` which is required by axum
        .map_response_body(boxed)
        .compression()
        .insert_response_header_if_not_present(
            header::CONTENT_TYPE,
            HeaderValue::from_static("application/json"),
        );
    let pool = PgPool::connect(&env::var("DATABASE_URL").expect("Expected DATABASE_URL"))
        .await
        .unwrap();
    if let Err(error) = MIGRATOR.run(&pool).await {
        tracing::error!("Migration error: {}", error);
        std::process::exit(1);
    }
    let dathost = DathostClient::new().expect("unable to create Dathost client");
    let discord = DiscordClient::new().expect("unable to create Discord Client");
    let steam = SteamClient::new().expect("unable to create Steam Client");

    let region = env::var("AWS_REGION").unwrap_or_else(|_| "auto".to_string());
    let endpoint = env::var("AWS_ENDPOINT").expect("AWS_ENDPOINT must be set");
    let bucket = Bucket::new(
        env::var("BUCKET_NAME")
            .expect("BUCKET_NAME must be set")
            .as_str(),
        Region::Custom { region, endpoint },
        Credentials::default().unwrap(),
    )
    .expect("unable to connect to S3 bucket");

    let shared_state = AppState {
        db: pool,
        dathost,
        bucket,
        discord,
        steam,
    };

    let app = Router::new()
        .nest("/api", routes())
        .with_state(shared_state)
        .layer(middleware);

    let addr = SocketAddr::from(([127, 0, 0, 1], 3000));
    tracing::info!("listening on {}", addr);
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}
