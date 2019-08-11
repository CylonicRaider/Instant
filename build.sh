#!/bin/sh

set -e

with_colon_split () {
    local prefix suffix
    case $1 in
        *:*) prefix="${1%%:*}" suffix="${1#*:}" ;;
        *) prefix="$1" suffix="" ;;
    esac
    "$2" "$prefix" "$suffix"
}

extract_item () {
    local subdir="$1" file="$2" subdir_msg file_msg
    subdir_msg="${subdir:+ subdirectory "$subdir"}"
    file_msg="${file:+${subdir:+,} file "$file"}"
    echo "Extracting$subdir_msg$file_msg..."
    git archive "HEAD${subdir:+:"$subdir"}" $file | tar -xf -
}

install_item () {
    local src="deps/$package/$1" dest="src/${2:-"$1"}"
    echo "Installing $src to $dest..."
    cp -r "$src" "$dest"
}

do_fetch () {
    local item

    cd "deps/$package"

    echo "Fetching repository..."
    git clone --bare --depth 1 ${ref:+-b "$ref"} "$url" .git

    echo "Updating commit hash..."
    sed -i 's/^\(commit=\).*$/\1"'"$(git rev-parse HEAD)"'"/' MANIFEST

    if [ -n "$extract" ]; then
        for item in $extract; do
            with_colon_split "$item" extract_item
        done
    else
        extract_item "" ""
    fi

    if [ -f "../../deps-patches/$package.patch" ]; then
        echo "Applying patches..."
        patch -p0 < "../../deps-patches/$package.patch"
    fi

    echo "Deleting repository..."
    rm -rf .git
}

do_build () {
    local item

    echo "Building Java code..."
    javac -cp src -d src $(find "deps/$package/" -name '*.java')

    for item in $install; do
        with_colon_split "$item" install_item
    done

    echo "Installing manifest to src/$install_manifest/MANIFEST..."
    grep -e '^url=' -e '^ref=' -e '^commit=' "deps/$package/MANIFEST" \
        > "src/$install_manifest/MANIFEST"
}

command="$1"
package="$2"

url="" ref="" commit="" extract=""
build_depends="" install="" install_manifest=""
. "deps/$package/MANIFEST"

case $command in
    fetch) do_fetch ;;
    build) do_build ;;
    *) echo "$0: Unknown command $command!" >&2; exit 1 ;;
esac
