#!/bin/bash -e

if [ "$BASH_SOURCE" ]
then
  LOCATION="$(dirname "$BASH_SOURCE")"
else
  LOCATION="$(dirname "$0")"
fi

cd "$LOCATION"

if [ -z "$SPAWN_NO_INSTANT" ]
then
  setsid java -jar Instant.jar 8070 >>Instant.log 2>&1 &
else
  echo "instant: not spawned" >&2
fi
if [ -z "$SPAWN_NO_SCRIBE" ]
then
  setsid ./scribe.py --read-file scribe.log --maxlen 100 \
    ws://localhost:8070/room/welcome/ws >>scribe.log \
    2>>scribe.err.log &
else
  echo "scribe: not spawned" >&2
fi
