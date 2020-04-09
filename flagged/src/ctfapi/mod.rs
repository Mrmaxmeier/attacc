use regex::bytes::Regex;
use std::sync::Arc;
use tokio::sync::Mutex;

#[cfg(feature = "ctfapi-saarctf")]
mod saarctf;

#[cfg(feature = "ctfapi-ructf")]
mod ructf;

#[cfg(feature = "ctfapi-forcad")]
mod forcad;

pub struct Flag {
    flag: String,
    run_handle: Arc<Mutex<crate::events::SessionRunHandle>>,
}

impl Flag {
    pub fn new(flag: &str, run_handle: &Arc<Mutex<crate::events::SessionRunHandle>>) -> Self {
        Flag {
            flag: flag.to_string(),
            run_handle: run_handle.clone(),
        }
    }
    pub fn set_verdict(&self, verdict: String) {
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

pub trait Submitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()>;
}

struct NoopSubmitter;
impl Submitter for NoopSubmitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()> {
        for flag in batch {
            println!("NoopSubmitter: {}", &**flag);
        }
        Ok(())
    }
}

struct HttpSubmitter;
impl Submitter for HttpSubmitter {
    fn submit_batch(&self, _batch: &[Flag]) -> std::io::Result<()> {
        unimplemented!()
    }
}

pub struct CTFApi {
    pub name: String,
    pub flag_regex: Regex,
    pub submitter: Box<dyn Submitter + Sync + Send>,
}

fn ctf_apis() -> Vec<CTFApi> {
    vec![
        CTFApi {
            name: String::from("noop"),
            flag_regex: Regex::new(r"FLAG\{[a-zA-Z0-9-_]{32}\}").unwrap(),
            submitter: Box::new(NoopSubmitter),
        },
        #[cfg(feature = "ctfapi-saarctf")]
        saarctf::ctfapi(),
        #[cfg(feature = "ctfapi-ructf")]
        ructf::ctfapi(),
        #[cfg(feature = "ctfapi-forcad")]
        forcad::ctfapi(),
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
        let _ = apis.pop();
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
