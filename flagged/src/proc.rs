use crate::config::Target;
use crate::flaghandler::FlagHandler;

use regex::bytes::Regex;
use std::process::Stdio;
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::sync::Mutex;
use tokio::time;

pub struct ProcessConfig {
    pub print_stdout: bool,
    pub print_stderr: bool,
    pub flag_regex: Regex,
    pub flag_handler: Arc<Mutex<FlagHandler>>,
    pub timeout: Duration,
}

impl ProcessConfig {
    pub async fn spawn(&self, target: Arc<Target>) {
        let mut cmd = target.prepare();

        cmd.stdout(Stdio::piped());
        cmd.stderr(Stdio::piped());

        let mut child = match cmd.spawn() {
            Ok(child) => child,
            Err(err) => return eprintln!("cmd.spawn failed: {:?}", err),
        };

        let stdout = child
            .stdout
            .take()
            .expect("child did not have a handle to stdout");

        let stderr = child
            .stderr
            .take()
            .expect("child did not have a handle to stderr");

        let flag_regex = self.flag_regex.clone();
        let mut stdout_reader = BufReader::new(stdout).lines();
        let stdout_target = target.clone();
        let print_stdout = self.print_stdout;
        let flag_handler = self.flag_handler.clone();

        tokio::spawn(async move {
            let pkey = &*stdout_target.key;
            // FIXME: tokio bufreader probably breaks for non-utf8 output
            while let Ok(Some(line)) = stdout_reader.next_line().await {
                if print_stdout {
                    println!("{} | {}", pkey, line);
                }
                if flag_regex.is_match(line.as_bytes()) {
                    let flags = flag_regex
                        .find_iter(line.as_bytes())
                        .map(|flag| String::from_utf8_lossy(flag.as_bytes()))
                        .collect::<Vec<_>>();
                    let mut handler = flag_handler.lock().await;
                    for flag in &flags {
                        handler.submit(flag).await;
                    }
                }
            }
        });

        let mut stderr = BufReader::new(stderr).lines();
        let stderr_target = target.clone();
        let print_stderr = self.print_stderr;
        tokio::spawn(async move {
            let pkey = &*stderr_target.key;
            while let Ok(Some(line)) = stderr.next_line().await {
                if print_stderr {
                    eprintln!("{} | {}", pkey, line);
                }
            }
        });

        let mut delay = time::delay_for(self.timeout.clone());

        tokio::select! {
            _ = &mut delay => {
                if let Err(err) = child.kill() {
                    eprintln!("{}: failed to kill process: {:?}", target.key, err);
                }
                eprintln!("{}: killed due to missed deadline!", target.key);
            }
            status = &mut child => {
                let status = status.expect("child process encountered an error");
                if print_stderr || print_stdout {
                    println!("{}: {}", target.key, status);
                }
            }
        };
    }
}
