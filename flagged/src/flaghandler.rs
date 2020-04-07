use bloom::{BloomFilter, ASMS};
use regex::bytes::Regex;

// Bloom filter tuned for high accuracy. These settings consume about 2 MB
const FLAG_HISTORY_LIMIT: u32 = 1_000_000;
const FLAG_FALSE_POSITIVE_RATE: f32 = 0.00001;

pub struct FlagHandler {
    seen: BloomFilter,
    uniques: u64,
}

impl FlagHandler {
    pub fn new() -> Self {
        let seen: BloomFilter =
            BloomFilter::with_rate(FLAG_FALSE_POSITIVE_RATE, FLAG_HISTORY_LIMIT);
        FlagHandler { seen, uniques: 0 }
    }

    pub fn submit(&mut self, flag: &str) -> bool {
        if self.seen.contains(&flag) {
            return false;
        }

        self.seen.insert(&flag);
        self.uniques += 1;
        if self.uniques > 1 && self.uniques.is_power_of_two() {
            println!(
                "STAT: {} unique flags ({:.04}% of expected bloom size)",
                self.uniques,
                (self.uniques as f64 / FLAG_HISTORY_LIMIT as f64) * 100.0
            );
        }
        self.submit_unique(flag);
        true
    }

    fn submit_unique(&self, flag: &str) {
        println!("UNIQ: {}", flag);
        todo!()
    }
}

#[derive(Clone)]
pub struct FlagParser {
    pub regex: Regex,
}

impl FlagParser {
    pub fn new(regex: &str) -> Self {
        FlagParser {
            regex: Regex::new(regex).expect("invalid flag regex"),
        }
    }
}
