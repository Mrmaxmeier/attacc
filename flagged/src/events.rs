use chrono::DateTime;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use uuid::Uuid;

pub struct Session {}

#[derive(Serialize, Deserialize, Debug)]
pub struct SessionAnnouncement {
    session_id: Uuid,
    hostname: String,
    path: String,
    config: crate::config::Config,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Run {
    id: Uuid,
    target_pkey: String,
    target: HashMap<String, serde_json::Value>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Counters {
    flags: u64,
    unique_flags: u64,
    runs: u64,
    intervals: u64,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Event {
    session_id: Uuid,
    timestamp: DateTime<chrono::offset::Utc>,
    payload: EventPayload,
}

#[derive(Serialize, Deserialize, Debug)]
pub enum EventPayload {
    SessionAnnouncement(SessionAnnouncement),
    IntervalStart,
    IntervalStop,
    RunStart(Run),
    RunStop {
        run: Run,
        exit_code: i32,
    },
    StdoutLine {
        run: Run,
        line: String,
    },
    StderrLine {
        run: Run,
        line: String,
    },
    FlagMatch {
        run: Run,
        flag: String,
    },
    FlagPending {
        run: Run,
        flag: String,
    },
    FlagVerdict {
        run: Run,
        flag: String,
        verdict: String,
    },
    Counters(Counters),
}
