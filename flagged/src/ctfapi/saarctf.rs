use crate::ctfapi::{CTFApi, Flag, Submitter};
use regex::bytes::Regex;
use std::io::{BufRead, BufReader, Write};

pub struct SaarctfSubmitter;

impl Submitter for SaarctfSubmitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()> {
        use std::net::TcpStream;
        let mut stream = TcpStream::connect("submission.ctf.saarland:31337")?;
        let mut data = Vec::new();
        for flag in batch {
            data.extend_from_slice(flag.as_bytes());
            data.push(b'\n');
        }
        stream.write_all(&data)?;
        let mut reader = BufReader::new(stream);
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
    let flag_regex = Regex::new(r"SAAR\{[A-Za-z0-9-_]{32}\}").unwrap();

    CTFApi {
        name: "saarctf".into(),
        flag_regex,
        submitter: Box::new(SaarctfSubmitter),
    }
}
