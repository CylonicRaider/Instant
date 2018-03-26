#!/bin/bash

# Init script for Instant with a Scribe copy in a specific room.

# Write a message to standard error.
log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S %Z')] $*"
}

# Obtain the status of the given process and print it to stdout.
proc_status() {
  service="$1"
  shift
  start-stop-daemon --status "$@"
  case $? in
    0) echo "$service: running" ;;
    1|3) echo "$service: not running" ;;
    *) echo "$service: error" ;;
  esac
}

set -e

cd "$(dirname "${BASH_SOURCE:-$0}")/.."

INSTANT_HOST=localhost
INSTANT_PORT=8080
INSTANT_COOKIES_KEYFILE=config/cookie-key.bin
INSTANT_OPTIONS=
SCRIBE_ROOM=welcome
SCRIBE_MAXLEN=100
SCRIBE_COOKIES=config/scribe-cookies.txt
SCRIBE_OPTIONS=
[ -f config/instant.default ] && . config/instant.default
export INSTANT_COOKIES_KEYFILE

cmd="$1"
shift
case $cmd in
  start)
    script/run.bash run-instant &
    echo $! > run/instant.pid
    script/run.bash run-scribe &
    echo $! > run/scribe.pid
  ;;
  run)
    trap 'kill $(jobs -p) 2>/dev/null' EXIT
    script/run.bash run-instant &
    echo $! > run/instant.pid
    script/run.bash run-scribe &
    echo $! > run/scribe.pid
    wait
  ;;
  stop)
    start-stop-daemon -K -p run/instant.pid -n java || true
    start-stop-daemon -K -p run/scribe.pid -n python3 || true
    rm -f run/instant.pid run/scribe.pid
  ;;
  status)
    set +e
    proc_status instant -p run/instant.pid -n java
    proc_status scribe -p run/scribe.pid -n python3
  ;;
  restart)
    script/run.bash stop
    exec script/run.bash start
  ;;
  bg-restart)
    [ "$1" = "-f" ] && trap 'kill $(jobs -p) 2>/dev/null' EXIT
    [ -p run/comm ] || mkfifo run/comm
    # Fire backend up in background.
    # Catting the pipe will block until someone opens and closes it. Hence,
    # we have enough time to perform the other jobs.
    script/run.bash run-instant --startup-cmd 'sh -c cat<run/comm' &
    echo $! > run/instant-new.pid
    # Wait for backend to become ready. Writing to the pipe blocks us until
    # there is a reader, i.e. the backend.
    exec 3>run/comm
    # Stop old backend; restart Scribe.
    start-stop-daemon -K -p run/instant.pid -n java
    mv run/instant-new.pid run/instant.pid
    start-stop-daemon -K -p run/scribe.pid -n python3 
    script/run.bash run-scribe 3>&- &
    echo $! > run/scribe.pid
    # Give the old processes some time to clean up.
    sleep 1
    # Actually run new backend.
    exec 3>&-
    [ "$1" = "-f" ] && wait
  ;;
  run-master)
    # Doing nothing on SIGHUP does not ignore the signal, but lets it
    # interrupt the wait builtin.
    trap ':' HUP
    # When interrupted, ensure the loop will not run (again), and interrupt
    # our children. With EXIT, we hope to not leave around child processes
    # should we die for other reasons.
    trap 'continue=; kill $(jobs -p) 2>/dev/null' INT TERM EXIT
    # Record our PID.
    echo $$ > run/master.pid
    # Most of the following commands will not finish "successfully" (e.g.
    # be killed of by signals; the kill-s in the traps also complain when
    # given nothing to signal).
    set +e
    # One the first round, start the backend normally.
    log "Starting service..."
    continue=y
    script/run.bash run &
    # Rate-limit restarts.
    sleep 10
    wait
    # Until we are told to stop, continue restarting the service in the
    # background.
    while [ "$continue" ]; do
      log "Restarting service..."
      script/run.bash bg-restart -f &
      sleep 10
      wait
    done
    # Remove our PID.
    rm -f run/master.pid
    log "Done."
  ;;
  run-instant)
    exec java -jar Instant.jar --host $INSTANT_HOST $INSTANT_PORT \
      --http-log log/Instant.log $INSTANT_OPTIONS "$@" \
      >>log/Instant.dbg.log 2>&1
  ;;
  run-scribe)
    export INSTABOT_RELAXED_COOKIES=y
    exec python3 script/scribe.py ${SCRIBE_MAXLEN:+--maxlen $SCRIBE_MAXLEN} \
      --msgdb db/messages-$SCRIBE_ROOM.sqlite \
      ${SCRIBE_COOKIES:+--cookies $SCRIBE_COOKIES} \
      ws://$INSTANT_HOST:$INSTANT_PORT/room/$SCRIBE_ROOM/ws \
      $SCRIBE_OPTIONS "$@" >>log/scribe.log 2>>log/scribe.err.log
  ;;
  *)
    echo "Unknown subcommand $cmd!" >&2
    exit 126
  ;;
esac
