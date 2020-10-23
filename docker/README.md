# Run OGEMA with FendoDB in Docker

Note: all shell commands likely require `sudo` on Linux.

## Prerequisites
* Docker installed
* this repo checked out
* a Linux shell (on Windows, use the git Bash)

## Build:
* `./build.sh`

## Run
* `./start.sh`
* Frontend: at [http://localhost:8443/ogema/index.html](http://localhost:8443/ogema/index.html) (a certificate warning must be accepted)
* Logs: `docker logs ogema-fendodb`
* OSGi-shell: Attach with `docker attach ogema-fendodb`, detach with `Ctrl-P, Ctrl-Q`.

## Stop
* `./stop.sh`


