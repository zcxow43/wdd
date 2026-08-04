#!/bin/sh
cd "$(dirname "$0")/.."
exec npm --prefix develop/frontend run dev
