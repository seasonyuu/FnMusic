#!/usr/bin/env bash

set -euo pipefail

readonly SEMVER_TAG_RE='^v(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$'
readonly MAX_VERSION_CODE=2100000000

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

commit_sha="$(git rev-parse --short=7 HEAD)"
commit_count="$(git rev-list --count HEAD)"

best_tag=""
best_distance=""
best_major=""
best_minor=""
best_patch=""

while IFS= read -r candidate_tag; do
    [[ "$candidate_tag" =~ $SEMVER_TAG_RE ]] || continue
    git merge-base --is-ancestor "$candidate_tag" HEAD || continue

    candidate_distance="$(git rev-list --count "${candidate_tag}..HEAD")"
    if [[ -z "$best_distance" || "$candidate_distance" -lt "$best_distance" ]]; then
        best_tag="$candidate_tag"
        best_distance="$candidate_distance"
        best_major="${BASH_REMATCH[1]}"
        best_minor="${BASH_REMATCH[2]}"
        best_patch="${BASH_REMATCH[3]}"
    fi
done < <(git tag --list --sort=-version:refname)

if [[ -n "$best_tag" ]]; then
    base_version_code=$((10#$best_major * 1000000 + 10#$best_minor * 1000 + 10#$best_patch))
    # Commit count keeps a new release above all preceding development builds.
    version_code=$((base_version_code + commit_count))

    if (( best_distance == 0 )); then
        version_name="${best_major}.${best_minor}.${best_patch}"
        exact_tag=true
    else
        version_name="${best_major}.${best_minor}.${best_patch}-dev.${best_distance}+${commit_sha}"
        exact_tag=false
    fi
    tag_name="$best_tag"
    distance="$best_distance"
else
    version_name="0.0.0-dev.${commit_count}+${commit_sha}"
    version_code="$commit_count"
    exact_tag=false
    tag_name=""
    distance="$commit_count"
fi

if (( version_code < 1 || version_code > MAX_VERSION_CODE )); then
    echo "Computed versionCode is outside the Android-supported range: $version_code" >&2
    exit 1
fi

printf 'versionName=%s\n' "$version_name"
printf 'versionCode=%s\n' "$version_code"
printf 'tagName=%s\n' "$tag_name"
printf 'exactTag=%s\n' "$exact_tag"
printf 'distance=%s\n' "$distance"
printf 'commitSha=%s\n' "$commit_sha"
