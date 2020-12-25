use std::collections::HashMap;

use crate::ctfapi::{CTFApi, Flag, Submitter};
use regex::bytes::Regex;

struct HttpSubmitter {
    token: String,
}

#[derive(serde::Deserialize)]
struct FlagResp {
    status: bool,
    flag: String,
    msg: String,
}

impl Submitter for HttpSubmitter {
    fn submit_batch(&self, batch: &[Flag]) -> std::io::Result<()> {
        let client = reqwest::blocking::Client::new();
        let flags = batch.iter().map(|f| f.flag.clone()).collect::<Vec<_>>();
        let resp = client
            .put("http://monitor.ructfe.org/flags")
            .header("X-Team-Token", self.token.clone())
            .json(&flags)
            .send()
            .map_err(|_| std::io::Error::new(std::io::ErrorKind::Other, "request failed"))?
            .json::<Vec<FlagResp>>()
            .map_err(|_| std::io::Error::new(std::io::ErrorKind::Other, "invalid json"))?;
        let flag_results = resp
            .iter()
            .map(|res| (res.flag.clone(), res))
            .collect::<HashMap<_, _>>();
        for flag in batch {
            if let Some(res) = flag_results.get(&flag.flag) {
                let _ = res.status; // TODO: set verdict accepted
                flag.set_verdict(res.msg.clone());
            }
        }
        Ok(())
    }
}

pub fn ctfapi() -> CTFApi {
    let flag_regex = Regex::new(r"\w{31}=").unwrap();

    CTFApi {
        name: "ructf".into(),
        flag_regex,
        test_flag: Some("PNFP4DKBOV6BTYL9YFGBQ9006582ADC=".into()),
        submitter: Box::new(HttpSubmitter {
            token: "196_63fdb1ed9ff2419cc7b000cf7e41982a".to_owned(),
        }),
    }
}
