# Developing Locally

Use ./gradlew shadowJar to build a standalone JAR

## Setting up a velocity proxy
1. run velocity > runVelocity
2. Inside /run, open `velocity.toml`
    1. player-info-forwarding-mode: "modern"
    2. Delete the forced hosts section (optional)
3. Open forward.secret and copy it

In your paper backend(s)
1. Open server.properites
    1. disable online-mode
       > This prevents the server from authenticating players, which the proxy will do instead
    2. Ensure the server-port matches up with what's in velocity.toml
2. Open config > paper-global.yml
    1. Enable the velocity section
    2. online-mode: true
    3. paste the forwarding secret

Once everything is running connect to the proxy server with `localhost:<port>`