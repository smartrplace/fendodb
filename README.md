# FendoDB timeseries database

----
## Overview
FendoDB is a fork of the OGEMA timeseries database ([SlotsDB](https://github.com/ogema/ogema/tree/public/src/core/ref-impl/recordeddata-slotsdb)), adding the following features:

* multiple database instances
* tagging time series / adding semantic information
* a full-fledged REST interface
* administrative shell commands
* query statistics about individual time series and groups of time series
* a visualization tool
* supports Java permissions
* besides the default one-file-per-day mode, other base intervals can be configured, such as hours, weeks or months

It is best used with [OGEMA](www.ogema.org), but can be integrated in a standalone Java-application as well. Some features are only available when used with OGEMA (REST user permissions, visualization), or at least with OSGi (REST interface, shell commands).

FendoDB requires Java 8 or higher.

---
## Getting started
Download the run configuration [rundir-ogema-felix](https://github.com/smartrplace/osgi-run-configs/raw/master/rundir-ogema-felix/rundir-ogema-felix.zip), and start it using one of the provided start scripts (see [https://github.com/smartrplace/osgi-run-configs](https://github.com/smartrplace/osgi-run-configs) for more information on configuration options). The rundir contains the compiled FendoDB bundles in the folder `init`. 

Use the [REST interface](https://github.com/smartrplace/fendodb/wiki/REST-API) or the [shell commands](https://github.com/smartrplace/fendodb/wiki/Shell-commands) to create a database instance and to add data points, or open the visualization page at https://localhost:8443/org/smartrplace/slotsdb/visualisation/index.html in the Browser.

See the [Wiki](https://github.com/smartrplace/fendodb/wiki) for more information.

---
## API reference
There is a [Java API](https://github.com/smartrplace/fendodb/wiki/Java-API) and a [REST interface](https://github.com/smartrplace/fendodb/wiki/REST-API), and in addition a set of [shell commands](https://github.com/smartrplace/fendodb/wiki/Shell-commands) for the Gogo OSGi shell.

----
## Build
Prerequisites: git, Java and Maven installed.

1. Clone this repository
2. In a shell, navigate to the base folder and execute `mvn clean install -DskipTests` 

----
## License
[GPL v3](https://www.gnu.org/licenses/gpl-3.0.en.html)

