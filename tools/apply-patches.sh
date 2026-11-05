#!/usr/bin/env bash
# Copyright (C) 2026 Navotpala Tech (Bliss Co-Labs Inc.)
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Navotpala-Commercial
#
# Apply retro-style's patches to the projects they change.
#
# The patches live in patches/<project path>/NNNN-*.patch, one folder per git project in your
# tree (for example patches/frameworks/base -> <tree>/frameworks/base). They are `git
# format-patch` output, so `git am` turns each one back into a commit with its author, date and
# message.
#
# Usage (from anywhere):
#   vendor/retro-style/tools/apply-patches.sh status   # which patches each project already has
#   vendor/retro-style/tools/apply-patches.sh check    # dry run in a throwaway worktree
#   vendor/retro-style/tools/apply-patches.sh apply    # git am the missing ones
#
# Tip: start a topic branch first so `repo sync` keeps your commits apart from upstream:
#   repo start retro-style frameworks/base packages/apps/Launcher3
#
# Environment: TOP=<tree root> (default: two levels above this repo, i.e. vendor/retro-style).
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
TOP="${TOP:-$(cd "$here/../.." && pwd)}"
patches="$here/patches"
cmd="${1:-status}"

say() { printf '%s\n' "$*"; }
run() { say "  \$ $*"; "$@"; }

# Every directory under patches/ that holds .patch files is a project path.
projects() {
	find "$patches" -name '*.patch' -printf '%h\n' | sort -u | sed "s#^$patches/##"
}

# A patch counts as applied when a commit with the same subject is in the project's history.
# git mailinfo reads the subject the way `git am` does (long subjects span two header lines).
subject() { git mailinfo /dev/null /dev/null <"$1" | sed -n 's/^Subject: //p'; }
# No `grep -q`: it stops reading at the first match, git log then dies of SIGPIPE, and pipefail
# turns that into "not applied".
applied() { git -C "$1" log --format=%s -n 500 | grep -Fx -- "$(subject "$2")" >/dev/null; }

status() {
	local project=$1 dir=$TOP/$1 p
	say "$project:"
	for p in "$patches/$project"/*.patch; do
		if applied "$dir" "$p"; then say "  [applied] $(basename "$p")"; else say "  [missing] $(basename "$p")"; fi
	done
}

# Apply the whole series to a temporary worktree at the project's HEAD, then remove it. Nothing
# in your checkout changes.
check() {
	local project=$1 dir=$TOP/$1 wt
	wt=$(mktemp -d)
	say "$project: checking in a throwaway worktree"
	run git -C "$dir" worktree add -q --detach "$wt" HEAD
	if missing_patches "$dir" | xargs -r git -C "$wt" -c user.name=check -c user.email=check@localhost am -q --3way; then
		say "  OK: every missing patch applies"
		git -C "$dir" worktree remove --force "$wt"
	else
		say "  FAILED: see the conflict above (worktree kept at $wt)"
		git -C "$wt" am --abort >/dev/null 2>&1 || true
		return 1
	fi
}

missing_patches() {
	local dir=$1 p
	for p in "$patches/${dir#"$TOP"/}"/*.patch; do
		applied "$dir" "$p" || printf '%s\n' "$p"
	done
}

apply() {
	local project=$1 dir=$TOP/$1 list
	list=$(missing_patches "$dir")
	if [ -z "$list" ]; then
		say "$project: already has every patch"
		return
	fi
	if [ -n "$(git -C "$dir" status --porcelain --untracked-files=no)" ]; then
		say "$project: has uncommitted changes; commit or stash them first"
		return 1
	fi
	say "$project: applying $(printf '%s\n' "$list" | wc -l) patch(es)"
	# --3way falls back to a three-way merge when the context moved a little.
	# If a patch conflicts: fix the files, `git add` them, then `git am --continue`.
	# shellcheck disable=SC2086
	run git -C "$dir" am --3way $list
}

case "$cmd" in
status | check | apply) ;;
*)
	say "usage: $0 status|check|apply"
	exit 2
	;;
esac

rc=0
for project in $(projects); do
	if [ ! -d "$TOP/$project/.git" ] && [ ! -f "$TOP/$project/.git" ]; then
		say "$project: not found under $TOP (set TOP=<tree root>)"
		rc=1
		continue
	fi
	"$cmd" "$project" || rc=1
done
exit $rc
