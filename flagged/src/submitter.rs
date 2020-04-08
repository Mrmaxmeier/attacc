use std::io::{BufRead, BufReader, Write};
use std::time::Duration;
use tokio::sync::mpsc;

const BATCH_SIZE_LIMIT: usize = 50;
const BATCH_TIME_LIMIT: Duration = Duration::from_millis(500);

pub struct TcpSubmitter {
    addr: std::net::SocketAddr,
}

impl TcpSubmitter {
    pub fn new(addr: std::net::SocketAddr) -> Self {
        TcpSubmitter { addr }
    }

    pub fn submit_batch(&self, batch: &[String]) -> std::io::Result<()> {
        use std::net::TcpStream;
        let mut stream = TcpStream::connect(self.addr)?;
        let mut data = Vec::new();
        for flag in batch {
            data.extend_from_slice(flag.as_bytes());
            data.push(b'\n');
        }
        stream.write_all(&data)?;
        let mut reader = BufReader::new(stream);
        let mut status = String::new();
        for flag in batch {
            let size = reader.read_line(&mut status)?;
            status.truncate(size);
            println!("{} -> {}", flag, status);
        }
        Ok(())
    }
}

pub struct FlagBatcher {
    pub tx: mpsc::Sender<String>,
}
impl FlagBatcher {
    pub fn start(submitter: TcpSubmitter) -> Self {
        let (tx, rx) = mpsc::channel(BATCH_SIZE_LIMIT);
        tokio::spawn(Self::watchdog(rx, submitter));
        FlagBatcher { tx }
    }

    async fn watchdog(mut rx: mpsc::Receiver<String>, submitter: TcpSubmitter) {
        let mut pending = Vec::new();
        loop {
            let item = rx.recv().await.unwrap();
            pending.push(item);
            let mut delay = tokio::time::delay_for(BATCH_TIME_LIMIT);
            loop {
                tokio::select! {
                    _ = &mut delay => {
                        break;
                    }
                    item = rx.recv() => {
                        pending.push(item.unwrap());
                        if pending.len() >= BATCH_SIZE_LIMIT { break; }
                    }
                }
            }

            while let Err(err) = submitter.submit_batch(&pending) {
                eprintln!("failed to submit batch: {:?}", err);
                eprintln!("retrying...");
                tokio::time::delay_for(BATCH_TIME_LIMIT).await;
            }
            pending.clear();
        }
    }

    pub async fn submit(&mut self, flag: String) {
        self.tx
            .send(flag)
            .await
            .expect("failed to send flag to FlagBatcher");
    }
}
