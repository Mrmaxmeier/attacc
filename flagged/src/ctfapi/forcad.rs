//! ForcAD submitter:
//! https://github.com/pomo-mondreganto/ForcAD/blob/master/backend/flag_submitter/tcp_server/server.py

use crate::ctfapi::{CTFApi, Submitter};
use regex::bytes::Regex;
use std::io::{BufRead, BufReader, Write};

pub struct ForcadSubmitter {
    addr: String,
    team_token: String,
}

impl Submitter for ForcadSubmitter {
    fn submit_batch(&self, batch: &[String]) -> std::io::Result<()> {
        use std::net::TcpStream;
        let mut stream = TcpStream::connect(&self.addr)?;

        stream.write_all(self.team_token.as_bytes())?;
        stream.write_all(b"\n")?;

        let mut data = Vec::new();
        for flag in batch {
            data.extend_from_slice(flag.as_bytes());
            data.push(b'\n');
        }
        stream.write_all(&data)?;
        let mut reader = BufReader::new(stream);

        let mut welcome = String::new();
        let size = reader.read_line(&mut welcome)?;
        welcome.truncate(size);
        assert_eq!(welcome, "Now enter your flags, one in a line:\n");

        let mut status = String::new();
        for flag in batch {
            let size = reader.read_line(&mut status)?;
            status.truncate(size);
            if status.ends_with('\n') {
                status.truncate(size - 1);
            }
            println!("{} -> {}", flag, status);
            // TODO: send to redis and persist to disk?
            status.clear();
        }
        Ok(())
    }
}

pub fn ctfapi() -> CTFApi {
    CTFApi {
        name: String::from("forcad"),
        flag_regex: Regex::new(r"\w{31}=").unwrap(),
        submitter: Box::new(ForcadSubmitter {
            addr: "10.10.10.10:31337".into(),
            team_token: "FIXME".into(), // TODO: --team-token or something? could also read from environment?
        }),
    }
}
