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
    pub async fn spawn(
        &self,
        target: Arc<Target>,
        mut run_handle: crate::events::SessionRunHandle,
    ) {
        let mut cmd = target.prepare();

        cmd.stdout(Stdio::piped());
        cmd.stderr(Stdio::piped());

        let mut child = match cmd.spawn() {
            Ok(child) => child,
            Err(err) => return eprintln!("cmd.spawn failed: {:?}", err),
        };

        run_handle.start();
        let run_handle = Arc::new(Mutex::new(run_handle));

        let stdout = child
            .stdout
            .take()
            .expect("child did not have a handle to stdout");

        let stderr = child
            .stderr
            .take()
            .expect("child did not have a handle to stderr");

        let flag_regex = self.flag_regex.clone();
        let mut stdout_reader = BufReader::new(stdout);
        let stdout_target = target.clone();
        let print_stdout = self.print_stdout;
        let flag_handler = self.flag_handler.clone();
        let stdout_run_handle = run_handle.clone();

        tokio::spawn(async move {
            let pkey = &*stdout_target.key;
            let mut buf = Vec::new();
            while let Ok(size) = stdout_reader.read_until(b'\n', &mut buf).await {
                if size == 0 {
                    break;
                }
                buf.truncate(size);
                if buf.ends_with(b"\n") {
                    buf.truncate(buf.len() - 1);
                }
                let line = String::from_utf8_lossy(&buf).to_string();
                if print_stdout {
                    println!("{} | {}", pkey, line);
                }
                stdout_run_handle.lock().await.stdout_line(line.clone());
                if flag_regex.is_match(&buf) {
                    let flags = flag_regex
                        .find_iter(line.as_bytes())
                        .map(|flag| String::from_utf8_lossy(flag.as_bytes()))
                        .collect::<Vec<_>>();
                    let mut handler = flag_handler.lock().await;
                    for flag in &flags {
                        handler.submit(flag, stdout_run_handle.clone()).await;
                    }
                }
                buf.clear();
            }
        });

        let mut stderr_reader = BufReader::new(stderr);
        let stderr_target = target.clone();
        let print_stderr = self.print_stderr;
        let stderr_run_handle = run_handle.clone();
        tokio::spawn(async move {
            let pkey = &*stderr_target.key;
            let mut buf = Vec::new();
            while let Ok(size) = stderr_reader.read_until(b'\n', &mut buf).await {
                if size == 0 {
                    break;
                }
                buf.truncate(size);
                if buf.ends_with(b"\n") {
                    buf.truncate(buf.len() - 1);
                }
                let line = String::from_utf8_lossy(&buf).to_string();
                if print_stderr {
                    eprintln!("{} | {}", pkey, line);
                }
                stderr_run_handle.lock().await.stderr_line(line.clone());
                buf.clear();
            }
        });

        tokio::select! {
            _ = time::sleep(self.timeout) => {
                if let Err(err) = child.kill().await {
                    eprintln!("{}: failed to kill process: {:?}", target.key, err);
                }
                eprintln!("{}: killed due to missed deadline!", target.key);
                run_handle.lock().await.timeout();
            }
            status = child.wait() => {
                let status = status.expect("child process encountered an error");
                if print_stderr || print_stdout {
                    println!("{}: {}", target.key, status);
                }
                run_handle.lock().await.exit(status.code());
            }
        };
    }
}
