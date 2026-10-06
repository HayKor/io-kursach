#!/bin/sh
# Экспорт каждой вкладки .drawio в отдельный PDF: <outdir>/<имя вкладки>.pdf
# usage: export.sh <file.drawio> <outdir>
set -e
src=$1
out=$2
mkdir -p "$out"
i=0
grep -o '<diagram [^>]*' "$src" | sed 's/.* name="\([^"]*\)".*/\1/' | while read -r name; do
    i=$((i + 1))
    drawio -x -f pdf --crop -p "$i" -o "$out/$name.pdf" "$src" >/dev/null 2>&1
    echo "  $name.pdf"
done
