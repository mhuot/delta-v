#!/bin/sh
grep -m1 '<version>' "$1" | sed 's/.*<version>\(.*\)<\/version>.*/\1/'
