use regex::bytes::Regex;
use std::sync::atomic;
use std::sync::Arc;
use tokio::sync::Mutex;

#[cfg(feature = "ctfapi-saarctf")]
mod saarctf;

#[cfg(feature = "ctfapi-ructf")]
mod ructf;

#[cfg(feature = "ctfapi-forcad")]
mod forcad;

#[cfg(feature = "ctfapi-faust")]
mod faust;

#[cfg(feature = "ctfapi-enowars")]
mod enowars;

#[cfg(feature = "ctfapi-mhackectf")]
mod mhackectf;

pub struct Flag {
    flag: String,
    run_handle: Arc<Mutex<crate::events::SessionRunHandle>>,
    has_verdict: atomic::AtomicBool,
}

impl Flag {
    pub fn new(flag: &str, run_handle: &Arc<Mutex<crate::events::SessionRunHandle>>) -> Self {
        Flag {
            flag: flag.to_string(),
            run_handle: run_handle.clone(),
            has_verdict: false.into(),
        }
    }

    pub fn set_verdict(&self, verdict: String) {
        let had_verdict = self.has_verdict.swap(true, atomic::Ordering::SeqCst);
        if had_verdict {
            eprintln!(
                "[WARN] duplicate verdict set for flag {}! ctfapi broken?",
                self
            );
        }
        println!("{} -> {}", self.flag, verdict);
        let run_handle = self.run_handle.clone();
        let flag = self.flag.clone();
        // FIXME: this is ugly and hides panics
        tokio::spawn(async move {
            run_handle.lock().await.flag_verdict(flag, verdict);
        });
    }
}

impl std::ops::Deref for Flag {
    type Target = str;
    fn deref(&self) -> &Self::Target {
        &self.flag
    }
}

impl std::fmt::Debug for Flag {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:?}", self.flag)
    }
}

impl std::fmt::Display for Flag {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.flag)
    }
}

impl Drop for Flag {
    fn drop(&mut self) {
        let verdict = self.has_verdict.load(atomic::Ordering::SeqCst);
        if !verdict {
            eprintln!(
                "[WARN] flag {} dropped without setting verdict! ctfapi broken?",
                self
            );
        }
    }
}

pub trait Submitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()>;
}

struct NoopSubmitter;
impl Submitter for NoopSubmitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()> {
        for flag in batch {
            flag.set_verdict(format!("NoopSubmitter: {}", &flag[5..8]));
        }
        Ok(())
    }
}

pub struct CTFApi {
    pub name: String,
    pub flag_regex: Regex,
    pub test_flag: Option<String>,
    pub submitter: Box<dyn Submitter + Sync + Send>,
}

fn ctf_apis() -> Vec<CTFApi> {
    vec![
        CTFApi {
            name: String::from("noop"),
            test_flag: None,
            flag_regex: Regex::new(r"FLAG\{[a-zA-Z0-9-_]{32}\}").unwrap(),
            submitter: Box::new(NoopSubmitter),
        },
        #[cfg(feature = "ctfapi-saarctf")]
        saarctf::ctfapi(),
        #[cfg(feature = "ctfapi-ructf")]
        ructf::ctfapi(),
        #[cfg(feature = "ctfapi-forcad")]
        forcad::ctfapi(),
        #[cfg(feature = "ctfapi-faust")]
        faust::ctfapi(),
        #[cfg(feature = "ctfapi-enowars")]
        enowars::ctfapi(),
        #[cfg(feature = "ctfapi-mhackectf")]
        mhackectf::ctfapi(),
    ]
}

pub fn choose(name: Option<String>) -> CTFApi {
    let mut apis = ctf_apis();

    if name == Some("help".into()) {
        println!("Supported backends:");
        for api in &apis {
            println!("- {}", api.name);
        }
        panic!();
    }

    if apis.len() == 2 {
        let target = apis.pop().unwrap();
        if let Some(name) = name {
            assert_eq!(target.name, name, "unable to satisfy ctfapi choice");
        }
        target
    } else {
        let name = name.expect(
            "flagged was compiled with multiple ctfapi backends. pass --ctf-api=help to list options",
        );
        for target in apis.into_iter() {
            if target.name == name {
                return target;
            }
        }
        panic!("unable to satisfy ctfapi choice");
    }
}
