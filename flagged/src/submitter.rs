use crate::ctfapi::{Flag, Submitter};
use std::time::Duration;
use tokio::sync::{mpsc, oneshot};

const BATCH_SIZE_LIMIT: usize = 50;
const BATCH_TIME_LIMIT: Duration = Duration::from_millis(1000);

pub struct FlagBatcher {
    pub tx: mpsc::Sender<Flag>,
    pub flushtx: mpsc::Sender<oneshot::Sender<()>>,
}
impl FlagBatcher {
    pub fn start(submitter: Box<dyn Submitter + Sync + Send>) -> Self {
        let (tx, rx) = mpsc::channel(BATCH_SIZE_LIMIT);
        let (flushtx, flushrx) = mpsc::channel(1);
        tokio::spawn(Self::watchdog(rx, flushrx, submitter));
        FlagBatcher { tx, flushtx }
    }

    async fn watchdog(
        mut rx: mpsc::Receiver<Flag>,
        mut flushrx: mpsc::Receiver<oneshot::Sender<()>>,
        submitter: Box<dyn Submitter + Sync + Send>,
    ) {
        let mut pending = Vec::new();
        loop {
            tokio::select! {
                chan = flushrx.recv() => {
                    chan.unwrap().send(()).unwrap();
                    continue;
                }
                item = rx.recv() => {
                    pending.push(item.unwrap());
                }
            }

            let mut ack_tx = None;

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
                    chan = flushrx.recv() => {
                        ack_tx = Some(chan.unwrap());
                        break;
                    }
                }
            }

            while let Err(err) = submitter.submit_batch(&pending) {
                eprintln!("failed to submit batch: {:?}", err);
                eprintln!("retrying...");
                tokio::time::delay_for(BATCH_TIME_LIMIT).await;
            }
            pending.clear();
            if let Some(ack_tx) = ack_tx {
                ack_tx.send(()).unwrap();
            }
        }
    }

    pub async fn submit(&mut self, flag: Flag) {
        self.tx
            .send(flag)
            .await
            .expect("failed to send flag to FlagBatcher");
    }

    pub async fn flush(&mut self) {
        let (tx, rx) = oneshot::channel();
        self.flushtx.send(tx).await.unwrap();
        rx.await.unwrap();
    }
}
