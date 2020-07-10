use crate::ctfapi::{CTFApi, Flag, Submitter};
use regex::bytes::Regex;
use std::io::{BufRead, BufReader, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::time::Duration;

pub struct FaustSubmitter;

impl Submitter for FaustSubmitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()> {
        let addr = "submission.faustctf.net:666"
            .to_socket_addrs()
            .expect("failed to resolve submission server")
            .next()
            .expect("failed to resovle submission server");
        let mut stream = TcpStream::connect_timeout(&addr, Duration::from_secs(1))?;
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
        assert_eq!(welcome, "Flag submission server\n");
        welcome.clear();

        let size = reader.read_line(&mut welcome)?;
        welcome.truncate(size);
        assert_eq!(welcome, "One flag per line please!\n");

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
    let flag_regex = Regex::new(r"FAUST_[A-Za-z0-9/\\\\+]{32}").unwrap();

    CTFApi {
        name: "faust".into(),
        flag_regex,
        submitter: Box::new(FaustSubmitter),
        test_flag: Some("FAUST_TESTTESTTESTTESTTESTTESTTESTTEST".into()),
    }
}
