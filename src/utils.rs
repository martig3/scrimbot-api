use crate::errors::Error;
use crate::models::{DathostMatchEnd, Player, SteamUser};
use crate::steam::SteamClient;

pub async fn end_of_match_msg(
    steam: &SteamClient,
    dathost_match: &DathostMatchEnd,
) -> Result<String, Error> {
    let steam_ids: Vec<u64> = dathost_match
        .players
        .iter()
        .map(|p| p.steam_id_64)
        .collect();
    let steam_users: Vec<SteamUser> = steam.get_player_summaries(steam_ids).await?;
    let mut team1_players: Vec<&Player> = dathost_match
        .players
        .iter()
        .filter(|p| p.team == "team1")
        .collect();
    team1_players.sort_by(|a, b| b.stats.damage_dealt.cmp(&a.stats.damage_dealt));
    let mut team2_players: Vec<&Player> = dathost_match
        .players
        .iter()
        .filter(|p| p.team == "team2")
        .collect();
    team2_players.sort_by(|a, b| b.stats.damage_dealt.cmp(&a.stats.damage_dealt));
    let mvp = if team1_players.first().unwrap().stats.damage_dealt
        > team2_players.first().unwrap().stats.damage_dealt
    {
        (
            steam_users
                .iter()
                .find(|u| u.steamid == team1_players.first().unwrap().steam_id_64)
                .unwrap(),
            team1_players.first().unwrap().stats.damage_dealt as f32
                / dathost_match.rounds_played as f32,
        )
    } else {
        (
            steam_users
                .iter()
                .find(|u| u.steamid == team2_players.first().unwrap().steam_id_64)
                .unwrap(),
            team2_players.first().unwrap().stats.damage_dealt as f32
                / dathost_match.rounds_played as f32,
        )
    };
    let mut msg = String::new();
    msg.push_str(
        format!(
            "**{} - {}** `{}`\n",
            dathost_match.team1.stats.score,
            dathost_match.team2.stats.score,
            dathost_match.settings.map
        )
        .as_str(),
    );
    msg.push_str("```md\n");
    msg.push_str("   Player              K   D   A   ADR     HS%     EF   ENT  1vX\n");
    msg.push_str("----------------------------------------------------------------\n");
    msg.push_str(dathost_match.team1.name.as_str());
    msg.push_str("\n");
    for (i, p) in team1_players.iter().enumerate() {
        msg.push_str(scoreboard_row(p, dathost_match.rounds_played, &steam_users, i + 1).as_str())
    }
    msg.push_str("\n");
    msg.push_str(dathost_match.team2.name.as_str());
    msg.push_str("\n");
    for (i, p) in team2_players.iter().enumerate() {
        msg.push_str(scoreboard_row(p, dathost_match.rounds_played, &steam_users, i + 1).as_str())
    }
    msg.push_str("```\n");
    msg.push_str("\n");
    msg.push_str(
        format!(
            "Congrats to the MVP `{}` with the highest ADR of `{:2}`!\n",
            mvp.0.personaname, mvp.1
        )
        .as_str(),
    );
    Ok(msg)
}

fn scoreboard_row(
    p: &Player,
    rounds_played: i32,
    steam_users: &Vec<SteamUser>,
    i: usize,
) -> String {
    let name = steam_users
        .iter()
        .find(|u| u.steamid == p.steam_id_64)
        .unwrap()
        .personaname
        .to_string();
    let name = format!("{:<19}", name);
    let name = truncate(name.as_str(), 19);
    let adr = format!(
        "{:.1}",
        (p.stats.damage_dealt as f32 / rounds_played.max(1) as f32)
    );
    let hs = format!(
        "{:.1}%",
        (p.stats.kills_with_headshot as f32 / p.stats.kills.max(1) as f32) * 100.0,
    );
    format!(
        "{i}. {:<20}{:<4}{:<4}{:<4}{:<8}{:<8}{:<5}{:<5}{:<5}\n",
        name,
        p.stats.kills,
        p.stats.deaths,
        p.stats.assists,
        adr,
        hs,
        p.stats.flashes_enemies_blinded,
        p.stats.entry_successes,
        p.stats.n1v_x_wins,
    )
}

fn truncate(s: &str, max_chars: usize) -> &str {
    match s.char_indices().nth(max_chars) {
        None => s,
        Some((idx, _)) => &s[..idx],
    }
}
