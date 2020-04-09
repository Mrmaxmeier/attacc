```
flagged - KISS Exploit-Thrower mit Niveau 

USAGE:
    flagged [FLAGS] [OPTIONS] [path]

ARGS:
    <path>    Working directory. If ommited, the current working directory will be used

FLAGS:
    -d, --debug          Debug mode: implies --concurrency=1 --stdout --stderr
        --dump-config    Dump configuration and exit
    -h, --help           Prints help information
        --stderr         Print stderr of exploits
        --stdout         Print stdout of exploits
    -V, --version        Prints version information

OPTIONS:
        --concurrency <concurrency>    Override config's concurrency setting
    -c, --config <config>              Config file path [default: attacc.json]
        --ctf-api <ctf-api>            Choose flag submission backend and flag regex. Only neccesary if flagged was
                                       compiled with multiple backends
        --interval <interval>          Override config's interval setting
        --stats-uri <stats-uri>        Report exploit status to redis. The URL format is
                                       redis://[:<passwd>@]<hostname>[:port][/<db>]
        --timeout <timeout>            Override config's timeout setting
```


Example usages:

```console
⋊> ~/_/a/example-exploit ⨯ flagged --ctf-api=noop
Starting interval...
UNIQ: FLAG{OWN_1fd75f057460bfbc54d6348de34f}
[...]
```

```console
cargo run -- ../example-exploit/ --interval 0.4 --timeout 0.3 --stats-uri redis://localhost --ctf-api=noop
```

Portable build with hard-coded ctfapi:

```console
⋊> ~/_/a/flagged $ cargo build --release --no-default-features --features ctfapi-saarctf --target x86_64-unknown-linux-musl
⋊> ~/_/a/flagged $ cp target/x86_64-unknown-linux-musl/release/flagged ../example-exploit/
⋊> ~/_/a/example-exploit $ ./flagged
Submitting test flag "SAAR{TESTTESTTESTTESTTESTTESTTESTTEST}"...
```