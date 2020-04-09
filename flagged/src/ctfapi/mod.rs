use regex::bytes::Regex;

#[cfg(feature = "ctfapi-saarctf")]
mod saarctf;

#[cfg(feature = "ctfapi-ructf")]
mod ructf;

#[cfg(feature = "ctfapi-forcad")]
mod forcad;

pub trait Submitter {
    fn submit_batch(&self, batch: &[String]) -> std::io::Result<()>;
}

struct NoopSubmitter;
impl Submitter for NoopSubmitter {
    fn submit_batch(&self, batch: &[String]) -> std::io::Result<()> {
        for flag in batch {
            println!("NoopSubmitter: {}", flag);
        }
        Ok(())
    }
}

struct HttpSubmitter;
impl Submitter for HttpSubmitter {
    fn submit_batch(&self, _batch: &[String]) -> std::io::Result<()> {
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
    if apis.len() == 2 {
        let _ = apis.pop();
        let target = apis.pop().unwrap();
        if let Some(name) = name {
            assert_eq!(target.name, name, "unable to satisfy ctfapi choice");
        }
        target
    } else {
        let name = name.expect(
            "flagged was compiled with multiple ctfapi backends. pass --ctf-api to choose one",
        );
        for target in apis.into_iter() {
            if target.name == name {
                return target;
            }
        }
        panic!("unable to satisfy ctfapi choice");
    }
}
