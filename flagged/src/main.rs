// TODOS:
// - fix non-utf8 stdout
// - publish data to redis
// => each pubsub entry should contain some kind of uuid for the session, timestamp
// => announce hostname, expoit name, config, ... on startup, keep this in redis `sessions/${uuid}`?
// => announce interval start/end
// => announce exploit run start, flags, stdout/err lines
// => announce pending flags
// => announce flag status returned by server
// - support multiple events without recompiling? => introduce key in attacc.json?
// - save stdout, stderr to disk?
// - save flag status to disk?
// - save interval index to disk => allow fair restarts?

use clap::Clap;
use futures::stream::{FuturesUnordered, StreamExt};
use std::sync::Arc;
use tokio::sync::Mutex;

use std::fs::File;
use std::path::Path;
use std::time::{Duration, Instant};

mod config;
mod flaghandler;
mod proc;
mod submitter;

use config::{Config, Target};
use submitter::FlagBatcher;

const FLAG_REGEX: &str = "FLAG_[a-zA-Z0-9-_]{32}";

const PRIMARY_KEY: &str = "IP";

#[derive(Clap, Debug)]
struct Opts {
    /// Config file path.
    #[clap(short = "c", long = "config", default_value = "attacc.json")]
    config: String,
    /// Report exploit status to redis. The URL format is redis://[:<passwd>@]<hostname>[:port][/<db>]
    #[clap(long = "stats-uri")]
    stats_uri: Option<String>,
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

    let flag_regex = regex::bytes::Regex::new(FLAG_REGEX).expect("invalid flag regex");

    if opts.debug {
        opts.concurrency = opts.concurrency.or(Some(1));
        opts.stdout = true;
        opts.stderr = true;
    }

    config.concurrency = opts.concurrency.unwrap_or(config.concurrency);
    config.interval = opts.interval.unwrap_or(config.interval);
    config.timeout = opts.timeout.unwrap_or(config.timeout);

    if opts.debug || opts.dump_config {
        config.explain(&flag_regex);
    }

    let redis_connection = opts.stats_uri.map(|uri| {
        redis::Client::open(uri)
            .expect("invalid redis uri")
            .get_connection()
            .expect("failed to connect to redis server")
    });

    if opts.dump_config {
        return Ok(());
    }

    let submitter = submitter::TcpSubmitter::new("127.0.0.1:31337".parse().unwrap());
    let flag_batcher = FlagBatcher::start(submitter);
    let flag_handler = Arc::new(Mutex::new(flaghandler::FlagHandler::new(flag_batcher)));

    let process_config = proc::ProcessConfig {
        flag_regex,
        flag_handler,
        print_stdout: opts.stdout,
        print_stderr: opts.stderr,
        timeout: Duration::from_secs_f64(config.timeout),
    };

    let mut jobs = FuturesUnordered::new();
    // NOTE: `active` vastly over-estimates actives jobs for well-behaving exploits
    let mut active = 0;

    let targets = config
        .targets
        .iter()
        .map(|target| {
            Arc::new(Target::new(
                PRIMARY_KEY,
                &config.command,
                target,
                folder.clone(),
            ))
        })
        .collect::<Vec<_>>();

    loop {
        println!("Starting interval...");
        let started_at = Instant::now();
        for target in &targets {
            if active == config.concurrency {
                jobs.next().await;
                active -= 1;
            }
            jobs.push(process_config.spawn(target.clone()));
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
