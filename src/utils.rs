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
    team1_players.sort_by(|a, b| a.stats.damage_dealt.cmp(&b.stats.damage_dealt));
    let mut team2_players: Vec<&Player> = dathost_match
        .players
        .iter()
        .filter(|p| p.team == "team2")
        .collect();
    team2_players.sort_by(|a, b| a.stats.damage_dealt.cmp(&b.stats.damage_dealt));
    let mvp = if team1_players.first().unwrap().stats.damage_dealt
        > team2_players.first().unwrap().stats.damage_dealt
    {
        (
            steam_users
                .iter()
                .find(|u| u.steamid == team1_players.first().unwrap().steam_id_64)
                .unwrap(),
            team1_players.first().unwrap().stats.damage_dealt,
        )
    } else {
        (
            steam_users
                .iter()
                .find(|u| u.steamid == team2_players.first().unwrap().steam_id_64)
                .unwrap(),
            team2_players.first().unwrap().stats.damage_dealt,
        )
    };
    let mut msg = String::new();
    msg.push_str(
        format!(
            "**{} - {}** `{}`",
            dathost_match.team1.stats.score,
            dathost_match.team2.stats.score,
            dathost_match.settings.map
        )
        .as_str(),
    );
    msg.push_str("```md\n");
    msg.push_str("  Player              K   D   A   ADR     HS%     EF   ENT \n");
    msg.push_str("-----------------------------------------------------------\n");
    msg.push_str(dathost_match.team1.name.as_str());
    for p in team1_players {
        msg.push_str(scoreboard_row(p, dathost_match.rounds_played, &steam_users).as_str())
    }
    msg.push_str("\n");
    msg.push_str(dathost_match.team2.name.as_str());
    for p in team2_players {
        msg.push_str(scoreboard_row(p, dathost_match.rounds_played, &steam_users).as_str())
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

fn scoreboard_row(p: &Player, rounds_played: i32, steam_users: &Vec<SteamUser>) -> String {
    let name = steam_users
        .iter()
        .find(|u| u.steamid == p.steam_id_64)
        .unwrap()
        .personaname
        .to_string();
    let name = format!("{:<19}", name);
    let name = truncate(name.as_str(), 19);
    let adr = format!(
        "{:1}",
        (p.stats.damage_dealt.max(1) as f32 / rounds_played.max(1) as f32)
    );
    let hs = format!(
        "{:1}",
        (p.stats.kills_with_headshot.max(1) as f32 / p.stats.kills.max(1) as f32),
    );
    format!(
        "  {:<20}{:<4}{:<4}{:<4}{:<8}{:<8}{:<5}{:<4}\n",
        name,
        p.stats.kills,
        p.stats.deaths,
        p.stats.assists,
        adr,
        hs,
        p.stats.flashes_enemies_blinded,
        p.stats.entry_successes,
    )
}

fn truncate(s: &str, max_chars: usize) -> &str {
    match s.char_indices().nth(max_chars) {
        None => s,
        Some((idx, _)) => &s[..idx],
    }
}
