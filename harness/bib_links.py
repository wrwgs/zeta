#!/usr/bin/env python3
"""Extract citations and document URLs from the recovered UEBB bibliography pages.

`singlelit.cgi?citation=<key>` was the Technische Universitaet Berlin / UEBB group's
BibTeX front-end.  Each page is the raw output of a BibTeX run -- including the run log --
followed by the formatted entry: title, authors, date, abstract, and, where the group had
put the paper online, an "online article" link.

Two things make a naive scrape miss everything:

  * the anchors are emitted with newlines *inside* the tag --
    `<A href=\n"http://..."\n>online article` -- so a line-oriented `href="..."` match
    finds nothing at all;
  * the pages are latin1 (and contain SGML entities such as `B&uuml;ssow`).

The point of the extraction is the document URLs: the bibliography points at paper files
that are not reachable from any surviving index page, including ones outside `~wg/`.
"""
import argparse
import glob
import html
import os
import re
import sys
import urllib.parse

ANCHOR = re.compile(r"<a\s+href\s*=\s*\"?\s*([^\">\s]+)\s*\"?\s*>(.*?)</a>",
                    re.IGNORECASE | re.DOTALL)
FIELD = re.compile(r"<b>\s*(Author\(s\)|Date|Published in|In)\s*:?\s*</b>\s*(.*?)(?:<br|</p|<b>)",
                   re.IGNORECASE | re.DOTALL)
DOCEXT = (".ps", ".ps.gz", ".pdf", ".tex", ".dvi", ".bib", ".tar.gz", ".zip", ".html.gz")


def text(s):
    s = re.sub(r"<[^>]*>", " ", s)
    s = html.unescape(s)
    return re.sub(r"\s+", " ", s).strip()


def title_of(body):
    m = re.search(r"<title>(.*?)</title>", body, re.IGNORECASE | re.DOTALL)
    if m and text(m.group(1)):
        return text(m.group(1))
    m = re.search(r"<h[12][^>]*>(.*?)</h[12]>", body, re.IGNORECASE | re.DOTALL)
    return text(m.group(1)) if m else ""


def key_of(path):
    base = os.path.basename(path)
    m = re.search(r"citation(?:%3D|=)([A-Za-z0-9+._-]+)", urllib.parse.unquote(base))
    if m:
        return m.group(1).rstrip("&")
    return os.path.splitext(base)[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+", help="bibliography page files (globs ok)")
    ap.add_argument("--docs-only", action="store_true",
                    help="print just the deduped document URLs, one per line")
    args = ap.parse_args()

    files = []
    for p in args.paths:
        files += sorted(glob.glob(p)) if any(c in p for c in "*?[") else [p]

    docs, entries = {}, []
    for path in files:
        if not os.path.isfile(path):
            continue
        body = open(path, "rb").read().decode("latin1")
        key = key_of(path)
        urls = []
        for url, label in ANCHOR.findall(body):
            url = html.unescape(url.strip())
            if not url.startswith(("http:", "https:", "ftp:")):
                continue
            low = urllib.parse.urlparse(url).path.lower()
            if low.endswith(DOCEXT) or "online" in text(label).lower():
                urls.append(url)
                docs.setdefault(url, []).append(key)
        fields = {text(k): text(v) for k, v in FIELD.findall(body)}
        entries.append((key, title_of(body), fields, urls))

    if args.docs_only:
        for u in sorted(docs):
            print(u)
        return 0

    for key, ttl, fields, urls in entries:
        print("[%s] %s" % (key, ttl or "(no title)"))
        for k in ("Author(s)", "Date", "Published in", "In"):
            if fields.get(k):
                print("    %-12s %s" % (k + ":", fields[k][:160]))
        for u in urls:
            print("    doc:         %s" % u)
    print("\n%d page(s), %d distinct document URL(s)" % (len(entries), len(docs)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
