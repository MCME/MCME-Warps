# MCME-Warps
Warps for MCME


# String Normalisation
* Strip accents
* Remove apostrophes
* Lowercase

## Uses
* yml file name
  * Easy to read file names
* Keys of the warp hashmap
  * Unique normalised warp names

* Warp name arguments
  * Normalise user input

So that when comparing user input with warp names, regardless of case, accents, apostrophes the 
correct warp will be matched and returned


# Developing Locally
## Setting up a velocity proxy
1. In your IDE start a velocity server with the `run velocity` task in the gradle toolbar
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

You'll need to spin up paper servers separately