# brew, with its own shell code (Library/Homebrew/brew.sh) run by the shell running this: brew's
# launcher ends with exec /bin/bash -p brew.sh, so the launcher's code runs with $0 as brew (its
# paths come from it) and its own shell ($BASH: bash, or fsh's test shim) in place of /bin/bash
code=$(sed 's|/bin/bash -p|"${BASH}" -p|g' /usr/local/Homebrew/bin/brew)
exec "$BASH" -c "$code" /usr/local/bin/brew "$@"
