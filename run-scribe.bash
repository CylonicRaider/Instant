#!/bin/bash -e

while :
do
  python3 scribe.py ws://localhost:8070/room/welcome/ws
  sleep 10
done
