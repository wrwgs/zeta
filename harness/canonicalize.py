#!/usr/bin/env python3
"""Canonicalise ZETA dumps and session transcripts so they can serve as baselines.

Why this is needed: `Name.hashCode()` returns `System.identityHashCode(repr)` and
`Name.compareTo()` is a difference of identity hashes (src/util/Name.pizza), so every
`Set<Name>`/`Map<Name,_>` iterates in a JVM-address-dependent order.  Dumps are therefore
reproducible *within* one JVM run but not *across* runs.  See zesurrect-plan.md §7.3.

Measured over the whole corpus with `baseline.py --selftest`, against the REFERENCE build:

    stable already : text, lang-config, latex-fragment, unchecked-absy, pretty-text
    canonicalised  : nmz            (four separate unordered constructs; see below)
    NOT handled    : unchecked-env, checked-absy, checked-env

Against the PORTED build all nine are byte-stable and none needs canonicalising: the port fixes
the three root causes at the source -- `Name.hashCode`/`compareTo` (identity-hash based),
`FormatPrinter.buildHashtableFormat` (renders in hash order) and `FormatInfo`'s visited-set
(keyed by identity hash, so colliding objects were reported as already visited).  That is why
`baselines-ported/` holds all 19 units x 9 content types with an empty EXCLUDED.  Everything
below therefore describes a property of the reference, kept because the reference is still the
differential oracle for each ported class; it is applied to both sides, so it stays correct.

Sorting is sound here precisely because the reference's own order carries no information --
there is nothing to lose.  Four unordered constructs in `nmz` are handled, found by repeated
sampling and each verified by `--selftest`:

  * absolute paths in locators                        -> canon_paths
  * PARENTS / GLOBALS / LOCALS name sets              -> NMZ_NAME_LISTS
  * PROPERTIES, and nested schema property sequences  -> NMZ_DEF_LISTS, sort_schema_props
  * schema bound-variable sets, and the reducer's
    counter-generated names                           -> sort_binder_vars,
                                                         alpha_rename_generated

That took `nmz` from 1 of 8 units baselined to 4 of 8, including the whole Toolkit.

Still out of reach.  One residual `nmz` cause defeats first-appearance alpha-renaming: which
generated name binds which definition can swap while the sorted binder list stays identical, so
canonical naming would have to order names by the content of their defining property,
recursively.  And `unchecked-env`/`checked-absy`/`checked-env` are reflective `Term.toFormat`
renderings interleaving hash-ordered collections with multi-line wrapped entries and no entry
delimiter -- no safe textual canonicalisation.

Caveat worth keeping in mind: canonicalisation can only ever *mask* a difference, never
fabricate one, since it is applied identically to both sides being compared.  But masking is
the risk for a port's sensitivity, so the raw dumps are kept and `--selftest` validates
stability, not correctness.  Fixing `Name.hashCode`/`compareTo` at the source removes the need
for all of this, but needs a working from-source build (plan §3.4 route A).

Usage:
    canonicalize.py --type <content-type> <file>     # dump    -> stdout
    canonicalize.py --transcript <file>              # session -> stdout
"""
import argparse
import collections
import os
import re
import sys

# content types whose dumps are byte-stable across runs of the reference
STABLE = ["text", "lang-config", "latex-fragment", "unchecked-absy", "pretty-text"]
# content types this module can canonicalise
CANONICALISABLE = ["nmz"]
# content types with no safe textual canonicalisation (see module docstring).  Under the
# reference build these are not reproducible and so are excluded from baselining; under the
# ported build they are byte-stable and compared raw, which is strictly stronger.
UNCANONICALISABLE = ["unchecked-env", "checked-absy", "checked-env"]

# MicroZ section headers, in the order ZETA emits them
NMZ_SECTIONS = ["UNIT", "PARENTS", "GLOBALS", "LOCALS", "PROPERTIES"]
# Sections whose content is an unordered set of names (possibly starting on the header line).
# PARENTS belongs here too: although MZ.UnitDef declares it as a `Seq<Name>`, the sequence is
# built by walking a hash-ordered collection, so `PARENTS LTX:BBSpec#bbook LTX:Toolkit#Toolkit`
# and the reverse both occur for the same specification (measured: 3 of 12 runs).
NMZ_NAME_LISTS = {"PARENTS", "GLOBALS", "LOCALS"}
# sections whose content is an unordered set of `;`-terminated definitions
NMZ_DEF_LISTS = {"PROPERTIES"}


def canon_paths(text):
    """Replace machine-specific absolute paths, which ZETA embeds in locators."""
    text = re.sub(r"/[^\s,)]*/lib/ztoolkits/", "$ZETABASE/lib/ztoolkits/", text)
    text = re.sub(r"/[^\s,)]*/run/zetabase/", "$ZETABASE/", text)
    return text


def _split_sections(lines):
    """Yield (header_line_or_None, body_lines) for a MicroZ dump."""
    chunks, cur_head, cur_body = [], None, []
    for line in lines:
        is_header = (line[:1] and not line[:1].isspace()
                     and line.split(" ", 1)[0].strip() in NMZ_SECTIONS)
        if is_header:
            if cur_head is not None or cur_body:
                chunks.append((cur_head, cur_body))
            cur_head, cur_body = line, []
        else:
            cur_body.append(line)
    chunks.append((cur_head, cur_body))
    return chunks


def _split_top_level(text, sep=";"):
    """Split on `sep` occurrences that are not nested inside braces/brackets/parens."""
    parts, depth, start = [], 0, 0
    for i, c in enumerate(text):
        if c in "{[(":
            depth += 1
        elif c in "}])":
            depth -= 1
        elif c == sep and depth <= 0:
            parts.append(text[start:i])
            start = i + 1
    tail = text[start:]
    if tail.strip():
        parts.append(tail)
    return parts


# MZ.Expr.Schema renders as  "{" binder "\" freeVars " | " properties "}"  where freeVars is a
# `Set<Name>` (MZ.pizza: `case Schema(Expr binder, Set<Name> freeVars, Seq<Property> props)`),
# joined with "," by Format.list.  Being a Set, its order is hash order and carries no meaning,
# so sorting it is sound -- there is no information to lose.
#
# The list cannot be found by looking for the first "\" after "{": the binder is an arbitrary
# expression and routinely contains backslashes itself (`_\setminus_`, `\dom[]`).  Instead the
# pattern is anchored on the far end, where the signature is tight: a run of comma-separated
# *simple* identifiers (no backslashes, no parens) immediately preceding " | ", introduced by a
# backslash.  A run of one name needs no sorting, so at least two are required -- which means
# the rewrite only fires where there is genuinely something to reorder.
# Names here may be primed (`p'`), generated ones carry a tilde (`%l~5`), and Z names are
# routinely backslash-led (`\sigma`, `_\rel_`) -- so a leading backslash is part of the name.
# That makes the construct briefly ambiguous, since the list is *introduced* by a backslash
# too: `{%b\\sigma,f | ...}` is the separator plus the names [`\sigma`, `f`].  Taking the
# leftmost match resolves it, because the introducing backslash always comes first.
# Getting this wrong silently corrupts names: an earlier version, whose name class excluded
# backslashes, rewrote `\\sigma,f` to `\f,sigma` -- dropping `\sigma`'s backslash.
_NAME = r"\\?[A-Za-z_%][A-Za-z0-9_%'~]*"
BINDER_VARS = re.compile(
    r"\\(" + _NAME + r"(?:\s*,\s*" + _NAME + r")+)(?=\s*\|)")


def sort_binder_vars(text):
    """Sort the bound-variable list of every MicroZ schema expression in `text`."""
    def repl(m):
        names = [n.strip() for n in m.group(1).split(",")]
        return "\\" + ",".join(sorted(names))
    return BINDER_VARS.sub(repl, text)


# The reducer invents fresh names with a counter, and the counter value depends on the order in
# which it walked a hash-ordered collection, so the SAME specification can reduce to `%R1` in
# one run and `%R2` in the next.  The names are machine-generated and their identity is
# arbitrary, so renumbering them by order of first appearance is a sound alpha-conversion --
# applied after the entries have been sorted, so that "first appearance" is itself canonical.
#
# Only the *numbered* families are generated names (%R<n>, %c<n>, %a<n>).  Unnumbered ones
# (%r, %l, %b, %s) are per-scope and not counter-derived, and trailing decorations on user
# names (DATE%typegen, NAME%giventypeinfo) must be left alone -- hence the digit requirement
# and the look-behind that keeps the match from starting inside an identifier.
# A generated name is "%" + a family letter + a serial that may itself contain tildes
# (%R1, %c2, %a0, %l~5, and compounds such as %a1~2).  The serial is matched as a whole: an
# earlier version matched only the leading digits, so `%a1~2` was rewritten to `%a<n>~2` --
# corrupting the name.  Trailing decorations on user names (DATE%typegen) have no digits and
# are therefore not matched.
GENERATED_NAME = re.compile(r"(?<![A-Za-z0-9_'])%([A-Za-z])([0-9~]*[0-9])")
# ... the same, on the left of a definition: "%l~2 = Interaction (\alpha,\pi)"
GENERATED_DEF = re.compile(r"(?<![A-Za-z0-9_'])%([A-Za-z])([0-9~]*[0-9])(?=\s*=[^=])")


def alpha_rename_generated(text):
    """Renumber the reducer's generated names canonically.

    Numbering is driven by **order of definition**, not order of first appearance.  First
    appearance does not work: a generated name's first occurrence is usually in a schema's
    bound-variable list, which has already been sorted, so two runs see an identical list and
    map the names identically even when the underlying objects are swapped -- exactly the
    residual that defeated the earlier version:

        %l~2 = Interaction (..); %l~3 = _\\rel_ (..)      [run 1]
        %l~3 = Interaction (..); %l~2 = _\\rel_ (..)      [run 2]

    Definition sites are reliable because the properties containing them have already been
    sorted, and their sort key begins with a source locator, which is stable.  So walking the
    definitions in textual order visits the same *definitions* in both runs, whatever names
    they happen to carry.  Names with no definition (e.g. %c1, %c2 in a comprehension) are
    numbered afterwards by first appearance.
    """
    mapping, counters = {}, {}

    def assign(m):
        key = m.group(0)
        if key in mapping:
            return
        family = m.group(1)                        # the family letter: R, c, a, l, ...
        counters[family] = counters.get(family, 0) + 1
        mapping[key] = "%%%s%d" % (family, counters[family])

    for m in GENERATED_DEF.finditer(text):         # definitions first, in textual order
        assign(m)
    for m in GENERATED_NAME.finditer(text):        # then anything undefined
        assign(m)
    if not mapping:
        return text
    return GENERATED_NAME.sub(lambda m: mapping[m.group(0)], text)


def _match_brace(s, i):
    """s[i] must be '{'; return the index just past the matching '}'."""
    depth = 0
    for j in range(i, len(s)):
        if s[j] == "{":
            depth += 1
        elif s[j] == "}":
            depth -= 1
            if depth == 0:
                return j + 1
    return len(s)


def sort_schema_props(s):
    """Sort the property sequence of every schema expression `{ binder | p1; p2; ... }`.

    MZ.Expr.Schema's third component is a `Seq<Property>`, rendered by Format.list with "; "
    separators, but the sequence is built from a hash-ordered collection, so the same
    specification yields `{... | A; B}` in one run and `{... | B; A}` in the next.

    Only the brace / bar / semicolon skeleton is parsed -- never the expression grammar.  A
    Denotation holding a literal brace would confuse this, which is why the result is always
    validated by `baseline.py --selftest` rather than trusted.
    """
    out, i = [], 0
    while i < len(s):
        if s[i] == "{":
            end = _match_brace(s, i)
            out.append("{" + _canon_schema_group(s[i + 1:end - 1]) + "}")
            i = end
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def _canon_schema_group(inner):
    """Canonicalise the inside of one `{...}`: recurse, then sort the properties after '|'."""
    depth, bar = 0, -1
    for j, c in enumerate(inner):
        if c in "{[(":
            depth += 1
        elif c in "}])":
            depth -= 1
        elif c == "|" and depth == 0:
            bar = j
            break
    if bar < 0:
        return sort_schema_props(inner)
    head, tail = inner[:bar], inner[bar + 1:]
    props = [sort_schema_props(p).strip() for p in _split_top_level(tail, ";")]
    props = [p for p in props if p]
    return sort_schema_props(head) + "| " + "; ".join(sorted(props))


def canon_nmz(text):
    """Sort the unordered collections in a MicroZ dump.

    PARENTS, GLOBALS and LOCALS are sets of names, rendered as whitespace-separated tokens that
    may begin on the header line itself and wrap across indented continuation lines.  PROPERTIES
    is a set of `;`-terminated definitions, each one or more lines.  All iterate in hash order
    (see the module docstring); only UNIT is structural.

    Step order matters, and is the reason this is a pipeline rather than a set of rewrites:

      1. sort each property's *nested* schema property sequences -- their sort key starts with a
         source locator, so this order is canonical and independent of generated names;
      2. sort the top-level properties, likewise locator-keyed;
      3. renumber generated names by order of *definition* over that already-sorted text;
      4. only now sort the schema bound-variable lists, which hold the canonical names;
      5. re-sort the top-level properties, since step 3 may have changed their sort keys.
    """
    text = canon_paths(text)
    sections = []                       # [(keyword_or_None, kind, [lines])]
    for header, body in _split_sections(text.split("\n")):
        if header is None:
            sections.append((None, "raw", [l.rstrip() for l in body]))
            continue
        keyword = header.split(" ", 1)[0].strip()
        indent = "  "
        for b in body:
            if b.strip():
                indent = b[:len(b) - len(b.lstrip())]
                break
        if keyword in NMZ_NAME_LISTS:
            names = sorted((header[len(keyword):] + " " + " ".join(body)).split())
            sections.append((keyword, "names", [indent + nm for nm in names]))
        elif keyword in NMZ_DEF_LISTS:
            entries = []
            for d in _split_top_level(header[len(keyword):] + "\n" + "\n".join(body)):
                # collapse whitespace so re-wrapping cannot affect the ordering key, then (1)
                collapsed = sort_schema_props(" ".join(d.split()))
                if collapsed:
                    entries.append(indent + collapsed + ";")
            sections.append((keyword, "defs", sorted(entries)))          # (2)
        else:
            sections.append((keyword, "raw", [header.rstrip()]
                             + [l.rstrip() for l in body]))

    def render():
        lines = []
        for keyword, kind, body in sections:
            if keyword is not None and kind != "raw":
                lines.append(keyword)
            lines.extend(body)
        return "\n".join(lines)

    renamed = alpha_rename_generated(render())                           # (3)
    renamed = sort_binder_vars(renamed)                                  # (4)

    # (5) re-sort the definition sections now that the names in their keys are canonical
    out, i = [], 0
    lines = renamed.split("\n")
    for keyword, kind, body in sections:
        n = len(body) + (1 if keyword is not None and kind != "raw" else 0)
        chunk = lines[i:i + n]
        i += n
        if kind == "defs":
            out.append(chunk[0])
            out.extend(sorted(chunk[1:]))
        else:
            out.extend(chunk)
    out.extend(lines[i:])
    return "\n".join(out)


_TOKEN = re.compile(r"\\?[A-Za-z_%][A-Za-z0-9_%~']*")


def _name_multiset(text):
    """Multiset of name tokens, insensitive to the things canonicalisation may legitimately
    change: the order of a binder list, whether the introducing backslash is adjacent to the
    first name, and the serial numbers of generated names."""
    text = BINDER_VARS.sub(
        lambda m: " " + ",".join(sorted(n.strip() for n in m.group(1).split(","))), text)
    text = GENERATED_NAME.sub("%GEN", text)
    return collections.Counter(_TOKEN.findall(text))


def verify_nmz(raw):
    """Check that canon_nmz only reorders and renumbers -- that it never loses or mangles a
    name.  Returns (ok, lost, gained).

    Worth keeping: two successive versions of this module silently corrupted names.  One had a
    name class that excluded backslashes and rewrote `\\\\sigma,f` to `\\f,sigma`, dropping a
    backslash; the other matched only the leading digits of a serial and turned `%a1~2` into
    `%a<n>~2`.  Both produced *stable* output, so --selftest could not have caught them.
    """
    a = _name_multiset(canon_paths(raw))
    b = _name_multiset(canon_nmz(raw))
    return a == b, a - b, b - a


def canon_transcript(text, zetabase=None, session_dir=None):
    """Normalise a zdrive.py transcript: absolute paths and the banner year vary by machine."""
    out = []
    for line in text.split("\n"):
        line = line.rstrip()
        if zetabase:
            line = line.replace(zetabase, "$ZETABASE")
        if session_dir:
            line = line.replace(session_dir, ".")
        # the toolkit is reached through an absolute path under $ZETABASE
        line = re.sub(r"/[^\s]*/lib/ztoolkits/", "$ZETABASE/lib/ztoolkits/", line)
        line = re.sub(r"/[^\s]*/(?:recovered/tools|toolchain)/\S*", "$TOOLS", line)
        out.append(line)
    # No trailing-line surgery here.  This used to strip "= good bye." lines, to hide a
    # shutdown race -- which masked the symptom and still let the interleaved rendering
    # ("= = good bye." / "good bye.") through as a spurious difference.  The race is fixed at
    # the source in zdrive.py: `quit` is now the only shutdown path, so the transcript ends
    # with exactly one "= good bye." and that line is worth keeping, since it is the evidence
    # that the session shut down cleanly rather than being killed.
    while out and out[-1] == "":
        out.pop()
    return "\n".join(out) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--type")
    ap.add_argument("--transcript", action="store_true")
    ap.add_argument("--verify", action="store_true",
                    help="check that canonicalising an nmz dump preserves every name")
    ap.add_argument("--zetabase")
    ap.add_argument("--session-dir")
    ap.add_argument("file")
    args = ap.parse_args()

    text = open(args.file, encoding="latin-1").read()
    if args.verify:
        ok, lost, gained = verify_nmz(text)
        if ok:
            print("ok: every name preserved (%d distinct tokens)"
                  % len(_name_multiset(canon_nmz(text))))
            return 0
        print("CORRUPTION: lost %s gained %s" % (dict(lost), dict(gained)), file=sys.stderr)
        return 1
    if args.transcript:
        sys.stdout.write(canon_transcript(text, args.zetabase, args.session_dir))
        return 0
    t = args.type
    if t in UNCANONICALISABLE:
        print("refusing to canonicalise %s: no safe textual canonicalisation "
              "(see module docstring)" % t, file=sys.stderr)
        return 2
    if t == "nmz":
        sys.stdout.write(canon_nmz(text))
        return 0
    if t in STABLE:
        sys.stdout.write(canon_paths(text))
        return 0
    print("unknown content type %r" % t, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
