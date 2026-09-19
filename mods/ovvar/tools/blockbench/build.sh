#!/bin/sh
# The whole build: the plugin is the concatenation of src/*.js, in name order. No npm, no
# bundler, no minifier -- Vlad loads one readable file through File > Plugins > Load from file,
# and `git diff` on it is a diff of the sources.
set -e
cd "$(dirname "$0")"
cat src/*.js > ovvar.js
echo "built ovvar.js ($(wc -l < ovvar.js) lines from $(ls src/*.js | wc -l) sources)"
