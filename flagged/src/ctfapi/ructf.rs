use crate::ctfapi::{CTFApi, Submitter};
use regex::bytes::Regex;

struct HttpSubmitter;
impl Submitter for HttpSubmitter {
    fn submit_batch(&self, _batch: &[String]) -> std::io::Result<()> {
        unimplemented!()
    }
}

pub fn ctfapi() -> CTFApi {
    let flag_regex = Regex::new(r"\w{31}=").unwrap();

    CTFApi {
        name: "ructf".into(),
        flag_regex,
        submitter: Box::new(HttpSubmitter),
    }
}
