-- Add migration script here
create table matches
(
    id           SERIAL PRIMARY KEY,
    map          VARCHAR(50) NOT NULL,
    team1_score  INT         NOT NULL,
    team2_score  INT         NOT NULL,
    team1_name   Text        NOT NULL,
    team2_name   Text        NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);

create table match_stats
(
    id                      SERIAL PRIMARY KEY,
    steam_id                BIGINT      NOT NULL,
    match_id                SERIAL      NOT NULL references matches (id),
    team                    VARCHAR(10) NOT NULL,
    kills                   INT         NOT NULL,
    assists                 INT         NOT NULL,
    deaths                  INT         NOT NULL,
    adr                     FLOAT       NOT NULL,
    n2ks                    INT         NOT NULL,
    n3ks                    INT         NOT NULL,
    n4ks                    INT         NOT NULL,
    n5ks                    INT         NOT NULL,
    kills_with_headshot     INT         NOT NULL,
    kills_with_pistol       INT         NOT NULL,
    kills_with_sniper       INT         NOT NULL,
    damage_dealt            INT         NOT NULL,
    entry_attempts          INT         NOT NULL,
    entry_successes         INT         NOT NULL,
    flashes_thrown          INT         NOT NULL,
    flashes_successful      INT         NOT NULL,
    flashes_enemies_blinded INT         NOT NULL,
    utility_thrown          INT         NOT NULL,
    utility_damage          INT         NOT NULL,
    n1vX_attempts           INT         NOT NULL,
    n1vX_wins               INT         NOT NULL
);
