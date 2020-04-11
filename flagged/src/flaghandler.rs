use crate::events::SessionRunHandle;
use crate::{ctfapi::Flag, submitter::FlagBatcher};
use bloom::{BloomFilter, ASMS};
use std::sync::Arc;
use tokio::sync::Mutex;

// Bloom filter tuned for high accuracy. These settings consume about 2 MB
const FLAG_HISTORY_LIMIT: u32 = 1_000_000;
const FLAG_FALSE_POSITIVE_RATE: f32 = 0.00001;

pub struct FlagHandler {
    seen: BloomFilter,
    uniques: u64,
    flag_batcher: FlagBatcher,
}

impl FlagHandler {
    pub fn new(flag_batcher: FlagBatcher) -> Self {
        let seen: BloomFilter =
            BloomFilter::with_rate(FLAG_FALSE_POSITIVE_RATE, FLAG_HISTORY_LIMIT);
        FlagHandler {
            seen,
            flag_batcher,
            uniques: 0,
        }
    }

    pub async fn submit(&mut self, flag: &str, run_handle: Arc<Mutex<SessionRunHandle>>) -> bool {
        // TODO: remove this for perf and also to prevent spam?
        let is_unique = !self.seen.contains(&flag);
        run_handle
            .lock()
            .await
            .flag_match(flag.to_string(), is_unique);
        if !is_unique {
            return false;
        }

        run_handle.lock().await.flag_pending(flag.to_string());
        self.submit_unique(Flag::new(flag, &run_handle)).await;

        self.seen.insert(&flag);
        self.uniques += 1;

        if self.uniques > 1 && self.uniques.is_power_of_two() {
            println!(
                "STAT: {} unique flags ({:.04}% of expected bloom size)",
                self.uniques,
                (self.uniques as f64 / FLAG_HISTORY_LIMIT as f64) * 100.0
            );
        }

        true
    }

    async fn submit_unique(&mut self, flag: Flag) {
        println!("UNIQ: {}", flag);
        self.flag_batcher.submit(flag).await;
    }

    pub async fn flush(&mut self) {
        self.flag_batcher.flush().await;
    }
}
