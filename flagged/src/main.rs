use clap::Clap;
use futures::stream::{FuturesUnordered, StreamExt};
use std::sync::Arc;
use tokio::sync::Mutex;

use std::fs::File;
use std::path::Path;
use std::process::Stdio;
use std::time::{Duration, Instant};

mod config;
mod flaghandler;
mod submitter;

use config::{Config, Target};

const FLAG_REGEX: &str = "FLAG_[a-zA-Z0-9-_]{32}";

#[derive(Clap, Debug)]
struct Opts {
    /// Config file path.
    #[clap(short = "c", long = "config", default_value = "attacc.json")]
    config: String,
    /// Working directory. If ommited, the current working directory will be used
    path: Option<String>,

    /// Debug mode: implies --concurrency=1 --stdout --stderr
    #[clap(short = "d", long = "debug")]
    debug: bool,
    /// Print stdout of exploits
    #[clap(long = "stdout")]
    stdout: bool,
    /// Print stderr of exploits
    #[clap(long = "stderr")]
    stderr: bool,

    /// Override config's concurrency setting
    #[clap(long = "concurrency")]
    concurrency: Option<u64>,
    /// Override config's interval setting
    #[clap(long = "interval")]
    interval: Option<f64>,
    /// Override config's timeout setting
    #[clap(long = "timeout")]
    timeout: Option<f64>,

    /// Dump configuration and exit
    #[clap(long = "dump-config")]
    dump_config: bool,
    // /// A level of verbosity, and can be used multiple times
    // #[clap(short = "v", long = "verbose", parse(from_occurrences))]
    // verbose: i32,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut opts: Opts = Opts::parse();

    let folder = opts.path.unwrap_or(".".into());
    let mut config_path = Path::new(&folder).to_path_buf();
    config_path.push(&opts.config);
    dbg!(&config_path);

    let config_file = File::open(config_path).expect("failed to open config");
    let mut config: Config = serde_json::from_reader(config_file).expect("failed to parse json");

    let flag_handler = Arc::new(Mutex::new(flaghandler::FlagHandler::new()));
    let flag_parser = flaghandler::FlagParser::new(FLAG_REGEX);

    if opts.debug {
        opts.concurrency = opts.concurrency.or(Some(1));
        opts.stdout = true;
        opts.stderr = true;
    }

    config.concurrency = opts.concurrency.unwrap_or(config.concurrency);
    config.interval = opts.interval.unwrap_or(config.interval);
    config.timeout = opts.timeout.unwrap_or(config.timeout);

    if opts.debug || opts.dump_config {
        config.explain(&flag_parser.regex);
    }

    if opts.dump_config {
        return Ok(());
    }

    let mut jobs = FuturesUnordered::new();
    // NOTE: `active` vastly over-estimates actives jobs for well-behaving exploits
    let mut active = 0;

    loop {
        println!("Starting interval...");
        let started_at = Instant::now();
        for target in &config.targets {
            if active == config.concurrency {
                jobs.next().await;
                active -= 1;
            }
            let target = Target::new(&config.command, target, folder.clone());
            let mut command = target.prepare();
            command.stdout(Stdio::piped());
            command.stderr(Stdio::piped());
            let proc = command.spawn().expect("failed to spawn process"); // TODO: make this configurable?
            jobs.push(async {
                proc.wait_with_output().await.unwrap();
            });
            active += 1;
        }

        // drain active jobs
        while active != 0 {
            jobs.next().await;
            active -= 1;
        }

        let elapsed = started_at.elapsed();
        if elapsed.as_secs_f64() >= config.interval {
            println!(
                "Late! Missed interval deadline by {:?}",
                elapsed - Duration::from_secs_f64(config.interval)
            );
        } else {
            println!(
                "Done! Snoozing for {:?}",
                Duration::from_secs_f64(config.interval) - elapsed
            );
            let deadline = started_at + Duration::from_secs_f64(config.interval);
            tokio::time::delay_until(tokio::time::Instant::from_std(deadline)).await;
        }
    }
}
