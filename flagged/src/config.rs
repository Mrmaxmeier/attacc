use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::time::Duration;

use crate::ctfapi::CTFApi;
use std::collections::HashMap;
use tokio::process::Command;

#[derive(Serialize, Deserialize, Debug)]
pub struct Config {
    pub command: Vec<String>,
    pub interval: f64,
    pub timeout: f64,
    pub concurrency: u64,
    pub targets: Vec<HashMap<String, Value>>,
}

impl Config {
    pub fn explain(&self, ctf_api: &CTFApi) {
        println!("Configuration:");
        println!("| ctf_api: {:?}", ctf_api.name);
        println!("| flag_regex: {:?}", ctf_api.flag_regex);
        println!("| concurrency: {:?}", self.concurrency);
        println!("| interval: {:?}", Duration::from_secs_f64(self.interval));
        println!("| timeout: {:?}", Duration::from_secs_f64(self.timeout));
        println!("| #targets: {:?}", self.targets.len());
        let batches = f64::ceil(self.targets.len() as f64 / self.concurrency as f64);
        let worst_case_interval = Duration::from_secs_f64(batches * self.timeout);
        println!("| ~> worst case interval length: {:?}", worst_case_interval);
        // TODO: explain target command templating
    }
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Target {
    pub args: Vec<String>,
    pub env: HashMap<String, String>,
    pub cwd: String,
    pub key: String,
}

impl Target {
    pub fn new(
        primary_key: &str,
        command_template: &[String],
        data: &HashMap<String, Value>,
        cwd: String,
    ) -> Target {
        let mut env = HashMap::new();
        let mut args = Vec::new();

        for (k, v) in data.iter() {
            let v = v
                .as_str()
                .map(|s| s.to_owned())
                .unwrap_or_else(|| serde_json::to_string(v).unwrap());
            env.insert(k.clone(), v);
        }

        for arg in command_template {
            let mut arg = arg.to_string();
            for (k, v) in env.iter() {
                arg = arg.replace(&format!("${}", k), v);
            }
            args.push(arg);
        }

        let key = env
            .get(primary_key)
            .expect("key PRIMARY_KEY (usually 'IP') missing for target")
            .clone();

        Target {
            env,
            args,
            cwd,
            key,
        }
    }

    pub fn prepare(&self) -> Command {
        let mut cmd = Command::new(&self.args[0]);
        for (k, v) in self.env.iter() {
            cmd.env(k, v);
        }
        for arg in self.args.iter().skip(1) {
            cmd.arg(arg);
        }
        cmd.current_dir(&self.cwd);
        cmd
    }
}
