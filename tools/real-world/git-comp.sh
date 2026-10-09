# source git's completion and prompt, then complete a few command lines (as bash would call them)
GC=/Library/Developer/CommandLineTools/usr/share/git-core
. $GC/git-completion.bash
. $GC/git-prompt.sh
complete -p git
comp() {
	local line="$1"
	COMP_LINE=$line
	COMP_POINT=${#line}
	read -ra COMP_WORDS <<< "$line"
	[[ $line == *' ' ]] && COMP_WORDS+=("")
	COMP_CWORD=$(( ${#COMP_WORDS[@]} - 1 ))
	COMPREPLY=()
	__git_wrap__git_main git "${COMP_WORDS[COMP_CWORD]}" "${COMP_WORDS[COMP_CWORD-1]}"
	printf '%s|' "$line"; printf '%s ' "${COMPREPLY[@]}" | tr ' ' '\n' | sort | tr '\n' ' '; echo
}
comp "git che"
comp "git checkout "
comp "git checkout ma"
comp "git commit --am"
comp "git log --pretty="
comp "git log --pretty=fu"
comp "git add "
comp "git config core.a"
comp "git config --get-"
comp "git remote "
comp "git branch -"
comp "git stash "
comp "git rebase -i HEAD~"
comp "git diff --stat "
comp "git help rev"
comp "git -C . sta"
comp "git sw"
comp "git switch "
comp "git tag "
comp "git push "
comp "git merge --"
GIT_PS1_SHOWDIRTYSTATE=1 GIT_PS1_SHOWUNTRACKEDFILES=1 GIT_PS1_SHOWSTASHSTATE=1 GIT_PS1_SHOWUPSTREAM=verbose
echo "ps1: [$(__git_ps1 ' (%s)')]"
echo dirty >> lib/a
echo "ps1: [$(__git_ps1 ' (%s)')]"
touch newfile
echo "ps1: [$(__git_ps1)]"
git checkout -q -b feature
echo "ps1: [$(__git_ps1 '<%s>')]"
git checkout -q HEAD~1 2>/dev/null
echo "ps1: [$(__git_ps1)]"
PROMPT_COMMAND='__git_ps1 "\u@\h:\w" "\\\$ "'
__git_ps1 "pre" "post"; echo "PS1=[$PS1]" | sed "s/$USER/USER/; s/$(hostname -s)/HOST/"
