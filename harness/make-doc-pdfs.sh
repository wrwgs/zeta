#!/usr/bin/env bash
# Regenerate the PDFs in doc/ from their PostScript originals, with real Type 1 fonts.
#
# Why this exists
# ---------------
# The 1997-2000 PostScript in doc/ is dvips output at -D600 with **PK bitmap fonts**:
#
#     %DVIPSCommandLine: dvips -D600 -o zeta.ps zeta.dvi
#     %DVIPSBitmapFont: Fa cmsy9
#
# Running ps2pdf on that embeds every glyph as a Type 3 bitmap. Three consequences:
#
#   1. Ghostscript 10 writes those Type 3 glyphs with a degenerate BBox. Poppler ignores it
#      and renders fine, but PDF.js (GitHub's viewer) and some Preview/PDFium builds clip
#      each glyph to the declared box and render **blank pages**. Ghostscript 8.15 did not
#      have this bug -- the 2007 third-party conversion in doc/other/sheffield-z2sal/ is
#      clean, which is how the regression was identified.
#   2. The text layer is a custom encoding with no ToUnicode, so extraction drops glyphs:
#      "Abstra t", " ustom fun tionality", "1:5" for "1.5".
#   3. Bitmaps do not scale, and are larger than the outlines they replace.
#
# pkfix (Heiko Oberdiek) reads the %DVIPSBitmapFont comments, looks up the real Type 1 CM
# font for each, and rewrites the PostScript to use it. ps2pdf on *that* yields a normal
# PDF: named Type 1C fonts with a ToUnicode map, correct boxes, selectable text, smaller.
#
# Eight of these documents come from dvips 5.58f (1997-98), which wrote "comments removed"
# and so emits no %DVIPSBitmapFont markers at all. pkfix cannot do anything with those. For
# them the script falls back to pkfix-helper (Scott Pakin), which identifies each bitmap font
# by comparing its glyph widths against every TFM it can find and writes the missing comments;
# pkfix then runs on its output. That identification is a **guess** -- the log reports ties
# such as "cmr10 tied with cmsl10", upright against slanted -- so a guessed conversion is
# accepted only if the rendered pages still match the original pixel for pixel (see below).
#
# Requires pkfix, pkfix-helper, fontware (for tftopl) from TeX Live, ghostscript, ImageMagick.
#
# A regenerated PDF is installed only if it is verifiably better: same page count, zero bad
# bounding boxes, no loss of extractable text, and no visible change to the page. The last
# check matters most and the first three cannot see it -- substituting a wrong font of the
# same widths leaves the page count, the boxes and the text intact while silently restyling
# the document. So each candidate is rasterised against the file it would replace and
# compared with ImageMagick RMSE. Calibration: a known-good conversion scores 0.085-0.096,
# two genuinely different pages score 0.20. The gate is 0.15.
#
# Not everything converts. asian97 and msz-lang keep a handful of Type 3 fonts that pkfix
# could not resolve; they render correctly and have valid boxes, but those particular glyphs
# stay outside the text layer. Run with SHOW_GUESSES=1 to see pkfix-helper's inferences.
#
# Nothing here is canonical -- the .ps/.ps.gz beside each PDF is the original, and these PDFs
# are a convenience rendering of it.

set -u
cd "$(dirname "$0")/.."
ZETA=$PWD

# TeX Live is not part of this repo and is not in PATH by default on every machine. Point
# TEXLIVE_BIN at its bin directory if the tools below are not already reachable.
: "${TEXLIVE_BIN:=}"
[ -n "$TEXLIVE_BIN" ] && export PATH="$TEXLIVE_BIN:$PATH"

# Fail loudly and up front. Without pkfix every document would report "no fonts converted,
# kept" and the run would look like a no-op rather than a missing dependency; without tftopl
# pkfix-helper silently reads zero TFMs and identifies nothing.
missing=
for t in pkfix pkfix-helper tftopl ps2pdf pdffonts pdfinfo pdftotext pdftoppm compare; do
  command -v "$t" >/dev/null 2>&1 || missing="$missing $t"
done
if [ -n "$missing" ]; then
  echo "missing required tool(s):$missing" >&2
  echo "pkfix, pkfix-helper and tftopl come from TeX Live:" >&2
  echo "    tlmgr install pkfix pkfix-helper fontware" >&2
  echo "set TEXLIVE_BIN to its bin directory if it is not in PATH." >&2
  exit 1
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# source PostScript : target PDF.  Not derivable by stripping the extension: zcompu.ps is the
# PostScript *variant* of zcompu00 (19 pp), while zcompu.pdf is a different, original PDF
# (18 pp) that must not be overwritten -- so the PS renders to zcompu-ps.pdf.
PAIRS="
doc/zeta/Z.ps.gz:doc/zeta/Z.pdf
doc/zeta/zeta.ps.gz:doc/zeta/zeta.pdf
doc/zeta/guide.ps.gz:doc/zeta/guide.pdf
doc/zeta/notes.ps.gz:doc/zeta/notes.pdf
doc/zeta/zapnotes.ps.gz:doc/zeta/zapnotes.pdf
doc/zeta/install.ps.gz:doc/zeta/install.pdf
doc/papers/apaqs01/apaqs.ps:doc/papers/apaqs01/apaqs.pdf
doc/papers/asian97/asian97.ps.gz:doc/papers/asian97/asian97.pdf
doc/papers/constr-prog-z00/ConstrProgInZ.ps:doc/papers/constr-prog-z00/ConstrProgInZ.pdf
doc/papers/dynz97/dynz.ps:doc/papers/dynz97/dynz.pdf
doc/papers/fase98/CyclicAgendaTSE.ps.gz:doc/papers/fase98/CyclicAgendaTSE.pdf
doc/papers/fm-trends98/fm-trends.ps.gz:doc/papers/fm-trends98/fm-trends.pdf
doc/papers/icfem00/ucases.ps:doc/papers/icfem00/ucases.pdf
doc/papers/idl99/idl99.ps.gz:doc/papers/idl99/idl99.pdf
doc/papers/ifm99/ModularFramework.ps.gz:doc/papers/ifm99/ModularFramework.pdf
doc/papers/lpar00/lpar00.ps:doc/papers/lpar00/lpar00.pdf
doc/papers/msz-lang97/msz-lang.ps.gz:doc/papers/msz-lang97/msz-lang.pdf
doc/papers/zcompu00/zcompu.ps:doc/papers/zcompu00/zcompu-ps.pdf
doc/papers/zetawhite00/zetawhite.ps.gz:doc/papers/zetawhite00/zetawhite.pdf
doc/papers/zstan98/deltaxi.ps.gz:doc/papers/zstan98/deltaxi.pdf
doc/papers/zstan98/freetype-recursion.ps.gz:doc/papers/zstan98/freetype-recursion.pdf
doc/papers/zstan98/multiple-decl.ps.gz:doc/papers/zstan98/multiple-decl.pdf
doc/papers/zstan98/paragraph-reordering.ps.gz:doc/papers/zstan98/paragraph-reordering.pdf
doc/papers/zstan98/section-reordering.ps.gz:doc/papers/zstan98/section-reordering.pdf
"

pages()   { pdfinfo "$1" 2>/dev/null | awk '/^Pages/{print $2}'; }

# Largest per-page RMSE between two PDFs, as a percentage (integer), over up to 8 pages spread
# across the document. Max rather than mean: one restyled page is a failure even if the rest
# match, and a mean over 30 pages would bury it.
maxrmse() {
  local a=$1 b=$2 n=$3 step i r worst=0
  step=$(( (n + 7) / 8 )); [ "$step" -lt 1 ] && step=1
  for (( i=1; i<=n; i+=step )); do
    pdftoppm -png -r 100 -gray -f $i -l $i "$a" "$WORK/ra" 2>/dev/null
    pdftoppm -png -r 100 -gray -f $i -l $i "$b" "$WORK/rb" 2>/dev/null
    local fa fb
    fa=$(ls "$WORK"/ra-*.png 2>/dev/null | head -1); fb=$(ls "$WORK"/rb-*.png 2>/dev/null | head -1)
    [ -n "$fa" ] && [ -n "$fb" ] || { rm -f "$WORK"/ra-*.png "$WORK"/rb-*.png; continue; }
    r=$(compare -metric RMSE "$fa" "$fb" null: 2>&1 | sed -n 's/.*(\([0-9.]*\)).*/\1/p')
    rm -f "$WORK"/ra-*.png "$WORK"/rb-*.png
    [ -n "$r" ] || continue
    r=$(awk -v x="$r" 'BEGIN{printf "%d", x*100}')
    [ "$r" -gt "$worst" ] && worst=$r
  done
  echo "$worst"
}
# Every page, not a sample: asian97 and msz-lang keep a few unconverted Type 3 fonts that
# appear only on later pages, and a three-page sample would have called them clean.
badbbox() { pdftoppm -png -r 30 "$1" /dev/null 2>&1 | grep -c 'Bad bounding box'; }
chars()   { pdftotext "$1" - 2>/dev/null | tr -d '[:space:]' | wc -c; }

printf '%-46s %-9s %-9s %s\n' DOCUMENT BEFORE AFTER RESULT
fixed=0; kept=0; failed=0

for pair in $PAIRS; do
  src=${pair%%:*}; dst=${pair##*:}
  [ -f "$ZETA/$src" ] || { printf '%-46s %-9s %-9s %s\n' "$dst" - - "no source"; failed=$((failed+1)); continue; }

  name=$(basename "$dst" .pdf)
  ps=$WORK/$name.ps
  case "$src" in
    *.gz) gunzip -c "$ZETA/$src" > "$ps" ;;
    *)    cp "$ZETA/$src" "$ps" ;;
  esac

  if [ -f "$ZETA/$dst" ]; then ob=$(badbbox "$ZETA/$dst"); op=$(pages "$ZETA/$dst"); oc=$(chars "$ZETA/$dst")
  else ob=-1; op=""; oc=0; fi

  # Stage 1: pkfix on the PostScript as it stands.
  pkfix "$ps" "$WORK/$name-t1.ps" > "$WORK/$name.pkfix.log" 2>&1
  conv=$(grep -c '^\*\*\* Font conversion' "$WORK/$name.pkfix.log")
  how=pkfix

  # Stage 2: dvips 5.58 wrote no font comments, so pkfix converted nothing. Let pkfix-helper
  # infer the fonts and write the comments, then run pkfix over its output. The TFM cache is
  # shared across documents -- building it takes longer than the conversion itself.
  if [ "$conv" -eq 0 ]; then
    how=guessed
    if pkfix-helper --cache="$WORK/tfm.cache" "$ps" "$WORK/$name-h.ps" > "$WORK/$name.helper.log" 2>&1 \
       && [ -s "$WORK/$name-h.ps" ]; then
      pkfix "$WORK/$name-h.ps" "$WORK/$name-t1.ps" > "$WORK/$name.pkfix.log" 2>&1
      conv=$(grep -c '^\*\*\* Font conversion' "$WORK/$name.pkfix.log")
      # Keep the inferences on the record. "cmr10 @ 1X ... tied with cmsl10" means the widths
      # alone could not tell upright from slanted, and that is what the RMSE gate below is
      # guarding against. If a glyph ever looks wrong in one of these eight, start here.
      if [ -n "${SHOW_GUESSES:-}" ]; then
        sed -n 's/^ *Processing /    guess: /p' "$WORK/$name.helper.log"
      fi
    fi
  fi

  if [ "$conv" -eq 0 ] || [ ! -s "$WORK/$name-t1.ps" ]; then
    printf '%-46s %-9s %-9s %s\n' "$dst" "bbox $ob" - "no fonts converted, kept"
    failed=$((failed+1)); continue
  fi

  if ! ps2pdf -dPDFSETTINGS=/prepress "$WORK/$name-t1.ps" "$WORK/$name.pdf" 2>"$WORK/$name.gs.log"; then
    printf '%-46s %-9s %-9s %s\n' "$dst" "bbox $ob" - "ps2pdf failed, kept"
    failed=$((failed+1)); continue
  fi

  nb=$(badbbox "$WORK/$name.pdf"); np=$(pages "$WORK/$name.pdf"); nc=$(chars "$WORK/$name.pdf")

  # Install only on a verifiable improvement. Page count must be identical, the new file must
  # have no bad boxes, and it must not have lost text. A PDF that renders but has fewer pages
  # or less text is a worse artifact than a broken-looking one, because the loss is silent.
  if [ -n "$op" ] && [ "$np" != "$op" ]; then
    printf '%-46s %-9s %-9s %s\n' "$dst" "$op pp" "$np pp" "PAGE COUNT CHANGED, kept"
    failed=$((failed+1)); continue
  fi
  if [ "$nb" -ne 0 ]; then
    printf '%-46s %-9s %-9s %s\n' "$dst" "bbox $ob" "bbox $nb" "still bad, kept"
    failed=$((failed+1)); continue
  fi
  if [ "$nc" -lt "$oc" ]; then
    printf '%-46s %-9s %-9s %s\n' "$dst" "$oc ch" "$nc ch" "TEXT LOST, kept"
    failed=$((failed+1)); continue
  fi
  if [ -f "$ZETA/$dst" ]; then
    rm=$(maxrmse "$ZETA/$dst" "$WORK/$name.pdf" "$np")
    if [ "$rm" -ge 15 ]; then
      printf '%-46s %-9s %-9s %s\n' "$dst" "bbox $ob" "rmse .$rm" "PAGE CHANGED ($how), kept"
      failed=$((failed+1)); continue
    fi
  else rm=0; fi

  if [ "$ob" -eq 0 ] && [ "$nc" -eq "$oc" ]; then
    printf '%-46s %-9s %-9s %s\n' "$dst" "bbox 0" "bbox 0" "already fine, kept"
    kept=$((kept+1)); continue
  fi

  cp "$WORK/$name.pdf" "$ZETA/$dst"
  printf '%-46s %-9s %-9s %s\n' "$dst" "bbox $ob" "bbox 0" "fixed $how, $conv fonts, +$((nc-oc)) ch, rmse .$rm"
  fixed=$((fixed+1))
done

echo
echo "$fixed regenerated, $kept already good, $failed kept as they were"
