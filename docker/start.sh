#!/bin/bash
MSYS_NO_PATHCONV=1 docker run -itd --rm --name ogema-fendodb -p 8080:8080 -p 8443:8443 ogema-fendodb

