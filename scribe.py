#!/usr/bin/env python3
# -*- coding: ascii -*-

import sys, time, json, threading
import websocket

# TODO: Peer log protocol.

NICKNAME = 'Scribe'

class Sequence:
    def __init__(self):
        self.value = 0
        self.lock = threading.Lock()
    def __call__(self):
        with self.lock:
            ret = self.value
            self.value += 1
        return ret
SEQUENCE = Sequence()
IDENTIFIER = Sequence()

class Cell:
    def __init__(self, value=None):
        self.value = value
        self.lock = threading.Lock()
    def get(self):
        with self.lock:
            return self.value
    def set(self, value):
        with self.lock:
            ret = self.value
            self.value = value
        return ret
GREETINGS = Cell()

def schedule_task(cell, delay, callback):
    task_id = IDENTIFIER()
    def wrapper():
        if cell.get() != task_id: return
        callback()
    threading.Timer(delay, callback).start()

def log(msg):
    sys.stdout.write('[%s] %s\n' % (
        time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime()), msg))
    sys.stdout.flush()

def send(ws, msg):
    log('SEND content=%r' % msg)
    ws.send(msg)
def send_unicast(ws, dest, msg):
    seq = SEQUENCE()
    send(ws, json.dumps({'type': 'unicast', 'seq': seq, 'to': dest,
                         'data': msg}))
    return seq
def send_broadcast(ws, msg):
    seq = SEQUENCE()
    send(ws, json.dumps({'type': 'broadcast', 'seq': seq, 'data': msg}))
    return seq

def on_open(ws):
    def send_greetings():
        send_broadcast(ws, {'type': 'who'})
        send_broadcast(ws, {'type': 'log-query'})
    log('OPENED')
    schedule_task(GREETINGS, 1, send_greetings)
def on_message(ws, msg):
    log('MESSAGE content=%r' % msg)
    # Try to extract message parameters.
    try:
        data = json.loads(msg)
        if data.get('type') not in ('unicast', 'broadcast'):
            return
        msgt = data.get('data', {}).get('type')
        # Protocollary replies / other handling
        if msgt == 'who':
            # Own nick
            send_unicast(ws, data.get('from'), {'type': 'nick',
                                                'nick': NICKNAME})
        elif msgt == 'post':
            msgd = data.get('data', {})
            log('POST id=%r parent=%r nick=%r content=%r' %
                (data.get('id'), msgd.get('parent'), msgd.get('nick'),
                 msgd.get('text')))
        # Peer Log Protocol currently not supported. :(
    except (ValueError, TypeError, AttributeError) as e:
        import traceback
        traceback.print_exc()
        return
def on_error(ws, exc):
    log('ERROR reason=%r' % repr(exc))
def on_close(ws):
    log('CLOSED')

def main():
    if len(sys.argv) != 2:
        sys.stderr.write('USAGE: %s url\n' % sys.argv[0])
        sys.stderr.flush()
        sys.exit(1)
    log('SCRIBE version=1.0')
    log('STARTED url=%r' % sys.argv[1])
    app = websocket.WebSocketApp(sys.argv[1], on_open=on_open,
        on_message=on_message, on_error=on_error, on_close=on_close)
    try:
        app.run_forever()
    except KeyboardInterrupt:
        log('INTERRUPTED')

if __name__ == '__main__': main()
