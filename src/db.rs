use crate::errors::Error;
use crate::models::{DathostMatch, DathostMatchEnd};
use sqlx::types::time::OffsetDateTime;
use sqlx::{PgExecutor, PgPool};

pub async fn create_match(
    executor: impl PgExecutor<'_>,
    dathost_match: &DathostMatchEnd,
) -> Result<DathostMatch, Error> {
    Ok(sqlx::query_as!(
        DathostMatch,
        r#"insert into matches (map, team1_score, team2_score, team1_name, team2_name, completed_at)
            values ($1, $2, $3, $4, $5, $6) returning id, map, team1_score, team2_score, team1_name, team2_name, completed_at"#,
        dathost_match.settings.map,
        dathost_match.team1.stats.score,
        dathost_match.team2.stats.score,
        dathost_match.team1.name,
        dathost_match.team2.name,
        OffsetDateTime::now_utc(),
    )
    .fetch_one(executor)
    .await?)
}

pub async fn create_match_stats(
    executor: &PgPool,
    dathost_match: &DathostMatchEnd,
    match_id: i32,
) -> Result<(), Error> {
    for p in &dathost_match.players {
        sqlx::query!(
            r#"insert into match_stats (
       steam_id,
       match_id,
       team,
       kills,
       assists,
       deaths,
       adr,
       n2ks,
       n3ks,
       n4ks,
       n5ks,
       kills_with_headshot,
       kills_with_pistol,
       kills_with_sniper,
       damage_dealt,
       entry_attempts,
       entry_successes,
       flashes_thrown,
       flashes_successful,
       flashes_enemies_blinded,
       utility_thrown,
       utility_damage,
       n1vx_attempts,
       n1vx_wins) 
       values(
       $1,
       $2,
       $3,
       $4,
       $5,
       $6,
       $7,
       $8,
       $9,
       $10,
       $11,
       $12,
       $13,
       $14,
       $15,
       $16,
       $17,
       $18,
       $19,
       $20,
       $21,
       $22,
       $23,
       $24
       );"#,
            p.steam_id_64 as i64,
            match_id,
            p.team,
            p.stats.kills,
            p.stats.assists,
            p.stats.deaths,
            p.stats.damage_dealt.max(1) as f64 / dathost_match.rounds_played.max(1) as f64,
            p.stats.n2ks,
            p.stats.n3ks,
            p.stats.n4ks,
            p.stats.n5ks,
            p.stats.kills_with_headshot,
            p.stats.kills_with_pistol,
            p.stats.kills_with_sniper,
            p.stats.damage_dealt,
            p.stats.entry_attempts,
            p.stats.entry_successes,
            p.stats.flashes_thrown,
            p.stats.flashes_successful,
            p.stats.flashes_enemies_blinded,
            p.stats.utility_thrown,
            p.stats.utility_damage,
            p.stats.n1v_x_attempts,
            p.stats.n1v_x_wins,
        )
        .execute(executor)
        .await?;
    }
    Ok(())
}
