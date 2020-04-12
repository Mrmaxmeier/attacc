//! ForcAD submitter:
//! https://github.com/pomo-mondreganto/ForcAD/blob/master/backend/flag_submitter/tcp_server/server.py

use crate::ctfapi::{CTFApi, Flag, Submitter};
use regex::bytes::Regex;
use std::io::{BufRead, BufReader, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

pub struct ForcadSubmitter {
    addr: String,
    team_token: String,
}

impl Submitter for ForcadSubmitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()> {
        let addr = self
            .addr
            .to_socket_addrs()
            .expect("failed to resolve submission server")
            .next()
            .expect("failed to resovle submission server");
        let mut stream = TcpStream::connect_timeout(&addr, Duration::from_secs(1))?;
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
        assert_eq!(welcome, "Welcome! Please, enter your team token:\n");
        welcome.clear();

        let size = reader.read_line(&mut welcome)?;
        welcome.truncate(size);
        assert_eq!(welcome, "Now enter your flags, one in a line:\n");

        for flag in batch {
            let mut status = String::new();
            let size = reader.read_line(&mut status)?;
            status.truncate(size);
            if status.ends_with('\n') {
                status.truncate(size - 1);
            }
            flag.set_verdict(status);
        }
        Ok(())
    }
}

pub fn ctfapi() -> CTFApi {
    CTFApi {
        name: String::from("forcad"),
        test_flag: Some("TESTTESTTESTTESTTESTTESTTESTTES=".into()),
        flag_regex: Regex::new(r"\w{31}=").unwrap(),
        submitter: Box::new(ForcadSubmitter {
            addr: "10.10.10.10:31337".into(),
            team_token: "FIXME".into(), // TODO: --team-token or something? could also read from environment?
        }),
    }
}
