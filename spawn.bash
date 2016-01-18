#!/bin/bash -e

if [ "$BASH_SOURCE" ]
then
  LOCATION="$(dirname "$BASH_SOURCE")"
else
  LOCATION="$(dirname "$0")"
fi

cd "$LOCATION"

setsid java -jar Instant.jar 8070 >>Instant.log 2>&1 &
setsid ./run-scribe.bash >>scribe.log 2>>scribe.err.log &
