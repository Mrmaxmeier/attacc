use chrono::DateTime;
use redis::Commands;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use uuid::Uuid;

pub struct Session {
    session_id: Uuid,
    redis: Option<redis::Client>,
    connection: Option<redis::Connection>,
}

impl Session {
    pub fn open(redis: Option<redis::Client>, announcement: SessionAnnouncement) -> Self {
        let session_id = Uuid::new_v4();
        let mut connection = redis.as_ref().map(|client| {
            client
                .get_connection_with_timeout(std::time::Duration::from_secs(1))
                .expect("unable to connect to redis")
        });
        Self::publish(
            connection.as_mut(),
            session_id,
            EventPayload::SessionAnnouncement(announcement),
        );
        Session {
            connection,
            session_id,
            redis,
        }
    }

    fn publish(
        connection: Option<&mut redis::Connection>,
        session_id: Uuid,
        payload: EventPayload,
    ) {
        if let Some(connection) = connection {
            let timestamp = chrono::offset::Utc::now();
            let event = Event {
                payload,
                session_id,
                timestamp,
            };
            let event = serde_json::to_string(&event).expect("failed to serialize event");
            connection
                .publish::<_, _, ()>("events", event)
                .expect("failed to publish message to redis");
        }
    }

    pub fn run_handle(&self, target: &crate::config::Target) -> SessionRunHandle {
        let connection = self.redis.as_ref().map(|client| {
            client
                .get_connection_with_timeout(std::time::Duration::from_secs(1))
                .expect("unable to connect to redis")
        });

        let run = Run {
            id: Uuid::new_v4(),
            target: target.env.clone(),
            key: target.key.clone(),
        };

        SessionRunHandle {
            connection,
            session_id: self.session_id,
            run,
        }
    }

    pub fn start_interval(&mut self) {
        Self::publish(
            self.connection.as_mut(),
            self.session_id,
            EventPayload::IntervalStart,
        )
    }

    pub fn end_interval(&mut self) {
        Self::publish(
            self.connection.as_mut(),
            self.session_id,
            EventPayload::IntervalEnd,
        )
    }
}

pub struct SessionRunHandle {
    session_id: Uuid,
    connection: Option<redis::Connection>,
    run: Run,
}

impl SessionRunHandle {
    pub fn noop() -> Self {
        SessionRunHandle {
            session_id: Uuid::default(),
            connection: None,
            run: Run {
                id: Uuid::default(),
                target: HashMap::new(),
                key: "".into(),
            },
        }
    }
    fn publish(&mut self, payload: EventPayload) {
        Session::publish(self.connection.as_mut(), self.session_id, payload);
    }
    pub fn start(&mut self) {
        self.publish(EventPayload::RunStart(self.run.clone()))
    }
    pub fn timeout(&mut self) {
        self.publish(EventPayload::RunTimeout(self.run.clone()))
    }
    pub fn exit(&mut self, exit_code: Option<i32>) {
        self.publish(EventPayload::RunExit {
            run: self.run.clone(),
            exit_code,
        })
    }
    pub fn stdout_line(&mut self, line: String) {
        self.publish(EventPayload::StdoutLine {
            run: self.run.clone(),
            line,
        })
    }
    pub fn stderr_line(&mut self, line: String) {
        self.publish(EventPayload::StderrLine {
            run: self.run.clone(),
            line,
        })
    }
    pub fn flag_match(&mut self, flag: String, is_unique: bool) {
        self.publish(EventPayload::FlagMatch {
            run: self.run.clone(),
            is_unique,
            flag,
        })
    }
    pub fn flag_pending(&mut self, flag: String) {
        self.publish(EventPayload::FlagPending {
            run: self.run.clone(),
            flag,
        })
    }
    pub fn flag_verdict(&mut self, flag: String, verdict: String) {
        // TODO: write to disk
        self.publish(EventPayload::FlagVerdict {
            run: self.run.clone(),
            flag,
            verdict,
        })
    }
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SessionAnnouncement {
    pub hostname: String,
    pub path: String,
    pub config: crate::config::Config,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Run {
    id: Uuid,
    key: String,
    target: HashMap<String, String>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Event {
    session_id: Uuid,
    timestamp: DateTime<chrono::offset::Utc>,
    payload: EventPayload,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "t", content = "c")]
pub enum EventPayload {
    SessionAnnouncement(SessionAnnouncement),
    IntervalStart,
    IntervalEnd,
    RunStart(Run),
    RunTimeout(Run),
    RunExit {
        run: Run,
        exit_code: Option<i32>,
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
        is_unique: bool,
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
}
