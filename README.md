# Instant

A threaded chat platform.

## What is this?

Instant is a clone of the threaded chat platform
[*euphoria.io*](https://euphoria.io/). It arose out of the developer's
discontentment with some design decisions of the latter, and the desire to
make something like that himself.

## How do I use it?

If you just want to chat, there is an instance — *the* instance — running at
[instant.leet.nu](https://instant.leet.nu).

To post a message, type it and `Return`; multi-line messages can be assembled
using `Shift-Return`. You can navigate amongst other messages by using the
arrow keys, or click messages to reply to them (more) quickly. Clicking the
gear icon on the top-right reveals a few settings.

On mobile, if a UI item is too small to reach, you can freely zoom in to do
so.

### Running an own clone

Refer to [`docs/RUNNING.md`](docs/RUNNING.md) for an extensive guide on how
to run Instant.

**TL;DR**: The stock backend (as provided in every commit, unless someone
messes it up) can be put up quickly like `java -jar Instant.jar 8080`,
whereafter Instant should be available under
[localhost:8080](http://localhost:8080/).

## Whom to blame?

The “original” developer should be regularly present on the “upstream”
instance — look for *@Xyzzy* in
[&welcome](https://instant.leet.nu/room/welcome/). If he's not there, asking
around in [&xkcd on euphoria.io](https://euphoria.io/room/xkcd/) might help.
