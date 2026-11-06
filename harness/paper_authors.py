"""Classify each recovered paper by whether Grieskamp or Lepper is an AUTHOR.

Handles the formats actually present: LaTeX, RCS ,v, gzipped/plain PostScript, PDF, DVI.
For PostScript the author names appear as dvips (text)show strings, so the text has to be
reassembled before matching -- and letters are often split across strings ("Griesk amp").
"""
import gzip, os, re, subprocess, sys, collections
ROOT = sys.argv[1]
# dvips emits a word as several (text)show strings and splits it at arbitrary points:
# "Griesk amp", "Lepp er". Matching the plain name silently misses those -- it is what
# deleted TR02-08.ps, a Lepper paper, on the first pass. Allow optional space between
# every letter.
def _loose(name):
    return r"\s*".join(re.escape(c) for c in name)
OURS = re.compile("|".join(_loose(n) for n in ("grieskamp", "lepper")), re.I)
RCS  = re.compile(rb"author[ \t]+(wg|lepper)[ \t]*;")
AUTH = re.compile(r"\\(?:author|institute)\{", re.I)

def ps_text(data):
    try: s = data.decode("latin1", "replace")
    except Exception: return ""
    words = re.findall(r"\((?:[^()\\]|\\.)*\)", s)
    return re.sub(r"\s+", " ", " ".join(w[1:-1] for w in words))

def read(path):
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        try: raw = gzip.decompress(raw)
        except Exception: pass
    return raw

def author_blocks(txt):
    for m in AUTH.finditer(txt):
        i = txt.index("{", m.start()); d = 0
        for j in range(i, min(len(txt), i+2000)):
            if txt[j] == "{": d += 1
            elif txt[j] == "}":
                d -= 1
                if d == 0: yield txt[i:j]; break

def classify(path):
    low = path.lower()
    try: raw = read(path)
    except Exception: return None
    if RCS.search(raw): return "rcs-committer"
    txt = raw.decode("latin1", "replace")
    if low.endswith((".tex", ".tex,v", ".sty", ".bib", ".cls", ".txt")) or ",v" in low:
        for b in author_blocks(txt):
            if OURS.search(b): return "author-block"
        return None
    if raw[:4] == b"%PDF":
        try:
            t = subprocess.run(["pdftotext", path, "-"], capture_output=True, timeout=60).stdout.decode("latin1","replace")
        except Exception: t = ""
        head = re.sub(r"\s+"," ",t)[:2500]
        return "pdf-frontmatter" if OURS.search(head) else None
    if raw[:2] == b"%!" or b"%%Creator" in raw[:3000] or low.endswith((".ps",".ps.gz")):
        head = ps_text(raw)[:3500]
        return "ps-frontmatter" if OURS.search(head) else None
    if low.endswith((".dvi",".dvi.gz")) or raw[:2] == b"\xf7\x02":
        head = re.sub(r"[^\x20-\x7e]+"," ",txt)[:6000]
        return "dvi-frontmatter" if OURS.search(head) else None
    if low.endswith((".html",".htm")):
        head = re.sub(r"<[^>]*>"," ",txt)
        return "html-byline" if OURS.search(head[:3000]) else None
    return None

groups = collections.defaultdict(lambda: {"files":0,"ev":collections.Counter(),"where":[]})
for dirpath,_,fns in os.walk(ROOT):
    for fn in fns:
        p = os.path.join(dirpath,fn); rel = os.path.relpath(p,ROOT)
        parts = rel.split(os.sep)
        if parts[0]=="internal" and len(parts)>2: grp = os.sep.join(parts[:2])
        elif len(parts)>1: grp = parts[0]+os.sep+re.sub(r"\.(ps|pdf|dvi|html|gz|tex)(\.gz)?$","",parts[-1])[:44]
        else: grp = parts[0]
        g = groups[grp]; g["files"] += 1
        c = classify(p)
        if c:
            g["ev"][c]+=1
            if len(g["where"])<2: g["where"].append(rel)
keep=[(k,v) for k,v in sorted(groups.items()) if v["ev"]]
drop=[(k,v) for k,v in sorted(groups.items()) if not v["ev"]]
print("KEEP %d groups / DROP %d groups" % (len(keep),len(drop)))
print()
for k,v in keep:
    print("KEEP %-40s files=%-4d %s" % (k, v["files"], dict(v["ev"])))
    for w in v["where"]: print("          %s" % w)
