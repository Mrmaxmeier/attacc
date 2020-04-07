use std::io::{self, BufRead, BufReader, Read, Write};

pub(crate) struct LineStatusWrapper<R: Read, W: Write> {
    reader: BufReader<R>,
    writer: W,
}

impl<R: Read, W: Write> LineStatusWrapper<R, W> {
    pub(crate) fn new(reader: BufReader<R>, writer: W) -> Self {
        LineStatusWrapper { reader, writer }
    }

    fn submit(&mut self, lines: &[&str]) -> io::Result<Vec<String>> {
        let len = lines.len();
        let mut result = Vec::with_capacity(len);
        for _ in 0..len {
            let mut line = String::new();
            self.reader.read_line(&mut line)?;
            result.push(line);
        }
        Ok(result)
    }
}
