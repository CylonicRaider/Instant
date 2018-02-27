#!/bin/bash -e

# Init script for Instant with a Scribe copy in a specific room.

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

cd "$(dirname "${BASH_SOURCE:-$0}")/.."

INSTANT_HOST=localhost
INSTANT_PORT=8080
INSTANT_COOKIES_KEYFILE=cookie-key.bin
INSTANT_OPTIONS=
SCRIBE_ROOM=welcome
SCRIBE_MAXLEN=100
SCRIBE_OPTIONS=
[ -f instant.default ] && . instant.default
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
    script/run.bash stop || true
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
  run-instant)
    exec java -jar Instant.jar --host $INSTANT_HOST $INSTANT_PORT \
      --http-log log/Instant.log $INSTANT_OPTIONS "$@" \
      >>log/Instant.dbg.log 2>&1
  ;;
  run-scribe)
    export INSTABOT_RELAXED_COOKIES=y
    exec python3 script/scribe.py ${SCRIBE_MAXLEN:+--maxlen $SCRIBE_MAXLEN} \
      --msgdb db/messages-$SCRIBE_ROOM.sqlite \
      --cookies run/scribe-cookies.txt \
      ws://$INSTANT_HOST:$INSTANT_PORT/room/$SCRIBE_ROOM/ws \
      $SCRIBE_OPTIONS "$@" >>log/scribe.log 2>>log/scribe.err.log
  ;;
  *)
    echo "Unknown subcommand $cmd!" >&2
    exit 126
  ;;
esac
