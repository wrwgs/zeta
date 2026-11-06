#!/usr/bin/env python3
"""Mirror a site from the Wayback Machine, preserving paths.

Written to recover Wolfgang Grieskamp's TU Berlin pages and papers, which are otherwise lost.

Two things make a naive `wget` of web.archive.org useless here:

  * the CDX index keys on the exact URL form, and `~wg`, `%7Ewg` and `%257Ewg` are three
    different keys for the same directory -- querying only one finds a fraction of the files
    (7 URLs instead of 169);
  * fetching `/web/<ts>/<url>` returns the page with the Archive's navigation toolbar injected.
    The `id_` suffix -- `/web/<ts>id_/<url>` -- returns the original bytes.

Resumable: a file already present and non-trivial is skipped, so it can be re-run after the
Archive goes offline mid-way (which it does).
"""
import argparse
import os
import sys
import time
import urllib.parse
import urllib.request

CDX = "http://web.archive.org/cdx/search/cdx"
WB = "http://web.archive.org/web/{ts}id_/{url}"
UA = {"User-Agent": "Mozilla/5.0 (archival mirror; personal recovery)"}


class Outage(Exception):
    """The Archive answered, but with an interstitial instead of the thing asked for."""


# The Archive serves its outage interstitial with status 200, so neither urlopen nor a
# status check notices it.  A mirror that trusts the status writes a 6 KB "Temporarily
# Offline" page over every file it was asked to recover, and the damage is silent: the
# files exist, are non-trivial, and are skipped by the resume logic on the next run.
# Treat these bodies as retryable failures instead.
OUTAGE_MARKERS = (
    b"Internet Archive: Temporarily Offline",
    b"Internet Archive services are temporarily offline",
    b"<title>Wayback Machine: Service Unavailable",
    b"Hrm.</h1>",
)


def check_body(data, url):
    low = data[:4096]
    for m in OUTAGE_MARKERS:
        if m in low:
            raise Outage("archive interstitial (%d B) for %s" % (len(data), url))
    return data


def get(url, timeout=120, retries=6, backoff=5.0):
    """Fetch one URL, retrying through an Archive outage.

    The Archive goes down for minutes at a time and, while down, refuses the connection
    outright (curl reports http=000) rather than answering 5xx.  Without a retry a single
    outage burns the whole worklist: every remaining URL is recorded as a permanent
    failure, and the run has to be redone from the CDX index.  Back off geometrically and
    let the caller's resumability handle whatever is still missing at the end.
    """
    last = None
    for attempt in range(retries):
        if attempt:
            time.sleep(backoff * (2 ** (attempt - 1)))
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return check_body(r.read(), url)
        except Exception as e:
            last = e
    raise last


def index(prefix, limit=5000):
    """[(original, timestamp, mimetype, length)] for one URL prefix."""
    # This is a mirror: prefix matching is always what is wanted.  Accept a trailing '*'
    # (the CDX shorthand) as well, so both spellings of a prefix behave the same.
    prefix = prefix.rstrip("*")
    q = urllib.parse.urlencode({
        "url": prefix, "output": "text", "fl": "original,timestamp,mimetype,length",
        "collapse": "urlkey", "filter": "statuscode:200", "limit": str(limit),
        "matchType": "prefix",
    })
    try:
        body = get("%s?%s" % (CDX, q), retries=4).decode("utf-8", "replace")
    except Exception as e:
        print("  cdx failed for %s: %s" % (prefix, e))
        return []
    out = []
    for line in body.strip().split("\n"):
        parts = line.split()
        if len(parts) >= 2 and parts[0].startswith("http"):
            out.append((parts[0], parts[1], parts[2] if len(parts) > 2 else "",
                        parts[3] if len(parts) > 3 else "0"))
    return out


def local_path(root, original):
    p = urllib.parse.urlparse(original)
    path = urllib.parse.unquote(p.path)
    if path.endswith("/") or not path:
        path += "index.html"
    host = p.netloc.split(":")[0]
    rel = os.path.normpath(path.lstrip("/"))
    if rel.startswith(".."):
        rel = rel.replace("..", "_")
    full = os.path.join(root, host, rel)
    if p.query:
        full += "__" + urllib.parse.quote(p.query, safe="")
    return full


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default="recovered/web")
    ap.add_argument("--prefix", action="append", required=True,
                    help="URL prefix to mirror; repeatable")
    ap.add_argument("--delay", type=float, default=0.4)
    args = ap.parse_args()

    seen, entries = set(), []
    for pre in args.prefix:
        got = index(pre)
        print("  %-52s %4d entries" % (pre, len(got)))
        for e in got:
            if e[0] not in seen:
                seen.add(e[0])
                entries.append(e)
    print("\n%d unique URLs\n" % len(entries))

    ok = skip = fail = 0
    failures = []
    for original, ts, mime, length in sorted(entries):
        dest = local_path(args.root, original)
        if os.path.exists(dest) and os.path.getsize(dest) > 200:
            skip += 1
            continue
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        try:
            data = get(WB.format(ts=ts, url=original))
            if len(data) < 100 and b"<html" in data.lower():
                raise ValueError("looks like an error page (%d B)" % len(data))
            with open(dest, "wb") as f:
                f.write(data)
            ok += 1
            print("  ok    %8d B  %s" % (len(data), original))
        except Exception as e:
            fail += 1
            failures.append((original, str(e)[:60]))
            print("  FAIL  %s  (%s)" % (original, str(e)[:60]))
        time.sleep(args.delay)

    print("\n%d downloaded, %d already present, %d failed" % (ok, skip, fail))
    for u, e in failures[:20]:
        print("    %s  %s" % (u, e))
    return 0


if __name__ == "__main__":
    sys.exit(main())
