// TODOs:
// - save stdout, stderr to disk?
// - save flag status to disk?
// - save interval index to disk => allow fair restarts?
// - submit flags mode -> read from stdin

use clap::Clap;
use futures::stream::{FuturesUnordered, StreamExt};
use std::sync::Arc;
use tokio::sync::Mutex;

use std::fs::File;
use std::path::Path;
use std::time::{Duration, Instant};

mod config;
mod ctfapi;
mod events;
mod flaghandler;
mod proc;
mod submitter;

use config::{Config, Target};
use submitter::FlagBatcher;

const PRIMARY_KEY: &str = "IP";

#[derive(Clap, Debug)]
#[clap(name = "flagged - KISS Exploit-Thrower mit Niveau")]
struct Opts {
    /// Config file path.
    #[clap(short = 'c', long = "config", default_value = "attacc.json")]
    config: String,
    /// Report exploit status to redis. The URL format is redis://[:<passwd>@]<hostname>[:port][/<db>]
    #[clap(long = "stats-uri")]
    stats_uri: Option<String>,
    /// Working directory. If omitted, the current working directory will be used
    path: Option<String>,
    /// Choose flag submission backend and flag regex. Only neccesary if flagged was compiled with multiple backends
    #[clap(long = "ctf-api")]
    ctf_api: Option<String>,
    /// Team token might be required by submission backend
    #[clap(long = "token")]
    team_token: Option<String>,

    /// Debug mode: implies --concurrency=1 --stdout --stderr
    #[clap(short = 'd', long = "debug")]
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

    let folder = opts.path.unwrap_or_else(|| String::from("."));
    let mut config_path = Path::new(&folder).to_path_buf();
    config_path.push(&opts.config);

    let config_file = File::open(config_path).expect("failed to open config");
    let mut config: Config = serde_json::from_reader(config_file).expect("failed to parse json");

    if opts.debug {
        opts.concurrency = opts.concurrency.or(Some(1));
        opts.stdout = true;
        opts.stderr = true;
    }

    config.concurrency = opts.concurrency.unwrap_or(config.concurrency);
    config.interval = opts.interval.unwrap_or(config.interval);
    config.timeout = opts.timeout.unwrap_or(config.timeout);

    let ctf_api = ctfapi::choose(opts.ctf_api);
    let flag_regex = ctf_api.flag_regex.clone();

    if opts.debug || opts.dump_config {
        config.explain(&ctf_api);
    }

    let redis_client = opts
        .stats_uri
        .map(|uri| redis::Client::open(uri).expect("invalid redis uri"));

    let path = std::fs::canonicalize(&folder).unwrap();
    let hostname = hostname::get().unwrap().into_string().unwrap();
    let mut events_session = events::Session::open(
        redis_client,
        events::SessionAnnouncement {
            config: config.clone(),
            hostname,
            path: format!("{:?}", path),
        },
    );

    if opts.dump_config {
        return Ok(());
    }

    if let Some(test_flag) = ctf_api.test_flag.as_ref() {
        println!("Submitting test flag {:?}...", test_flag);
        let fake_run_handle = events::SessionRunHandle::noop();
        let flag = ctfapi::Flag::new(&test_flag, &Arc::new(Mutex::new(fake_run_handle)));
        ctf_api
            .submitter
            .submit_batch(&[flag])
            .expect("failed to submit test flag")
    }

    let flag_batcher = FlagBatcher::start(ctf_api.submitter);
    let flag_handler = Arc::new(Mutex::new(flaghandler::FlagHandler::new(flag_batcher)));

    let process_config = proc::ProcessConfig {
        flag_regex,
        flag_handler: flag_handler.clone(),
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
        events_session.start_interval();
        let started_at = Instant::now();
        for target in &targets {
            if active == config.concurrency {
                jobs.next().await;
                active -= 1;
            }
            let run_handle = events_session.run_handle(target);
            jobs.push(process_config.spawn(target.clone(), run_handle));
            active += 1;
        }

        // drain active jobs
        while active != 0 {
            jobs.next().await;
            active -= 1;
        }

        {
            flag_handler.lock().await.flush().await;
        }

        events_session.end_interval();

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
            tokio::time::sleep_until(tokio::time::Instant::from_std(deadline)).await;
        }
    }
}
