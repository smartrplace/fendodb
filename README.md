# FendoDB timeseries database

----
## Overview
FendoDB is a fork of the OGEMA timeseries database ([SlotsDB](https://github.com/ogema/ogema/tree/public/src/core/ref-impl/recordeddata-slotsdb)), adding the following features:

* multiple database instances
* tagging time series / adding semantic information
* a full-fledged REST interface
* administrative shell commands
* a visualization tool
* supports Java permissions
* besides the default one-file-per-day mode, other base intervals can be configured, such as hours, weeks or months

It is best used with [OGEMA](www.ogema.org), but can be integrated in a standalone Java-application as well. Some features are only available when used with OGEMA (REST user permissions, visualization), or at least with OSGi (REST interface, shell commands).

---
## Getting started
TODO

---
## API reference
There is a Java API and a REST interface, and in addition a set of shell commands for the Gogo OSGi shell.

TODO

----
## Build
Prerequisites: git, Java and Maven installed.

1. Clone this repository
2. In a shell, navigate to the base folder and execute `mvn clean install -DskipTests` 
3. TODO

----
## License
[GPL v3](https://www.gnu.org/licenses/gpl-3.0.en.html)

