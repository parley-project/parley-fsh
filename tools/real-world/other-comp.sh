# Homebrew's completion scripts for brew, cmake and gh, called as bash calls them
for f in brew cmake ctest gh; do . /usr/local/etc/bash_completion.d/$f; done
complete -p brew cmake gh 2>&1
comp() {
	local fn=$1 line="$2"
	COMP_LINE=$line
	COMP_POINT=${#line}
	read -ra COMP_WORDS <<< "$line"
	[[ $line == *' ' ]] && COMP_WORDS+=("")
	COMP_CWORD=$(( ${#COMP_WORDS[@]} - 1 ))
	COMPREPLY=()
	$fn "${COMP_WORDS[0]}" "${COMP_WORDS[COMP_CWORD]}" "${COMP_WORDS[COMP_CWORD-1]}" 2>&1
	printf '%s|' "$line"; printf '%s\n' "${COMPREPLY[@]}" | sort | head -40 | tr '\n' ' '; echo
}
comp _brew "brew ins"
comp _brew "brew --"
comp _brew "brew info --"
comp _brew "brew list --"
comp _brew "brew uninstall "
comp _brew "brew cleanup --"
comp _cmake "cmake --"
comp _cmake "cmake -D"
comp _cmake "cmake --help-"
comp _ctest "ctest --"
comp __start_gh "gh "
comp __start_gh "gh pr "
comp __start_gh "gh repo --"
