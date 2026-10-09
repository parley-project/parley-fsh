# Complete command lines with bash-completion (its loader finds each command's completion, as an
# interactive bash would), printing what each completes to. Args: bash-completion's directory, a
# file of bash's identity variables (for a shell that has none).
BC=$1
[[ -n ${BASH_VERSION-} ]] || . "$2"
export BASH_COMPLETION_USER_DIR=$PWD/no-user-dir BASH_COMPLETION_COMPAT_DIR=$PWD/no-compat-dir
. "$BC/bash_completion"
comp() {
	local line=$1 spec func cmd
	COMP_LINE=$line
	COMP_POINT=${#line}
	COMP_TYPE=9 COMP_KEY=9
	read -ra COMP_WORDS <<< "$line"
	[[ $line == *' ' ]] && COMP_WORDS+=("")
	COMP_CWORD=$(( ${#COMP_WORDS[@]} - 1 ))
	cmd=${COMP_WORDS[0]}
	COMPREPLY=()
	spec=$(complete -p -- "$cmd" 2>/dev/null)
	if [[ -z $spec ]]; then
		_comp_complete_load "$cmd" "${COMP_WORDS[COMP_CWORD]}" "${COMP_WORDS[COMP_CWORD-1]}"
		spec=$(complete -p -- "$cmd" 2>/dev/null)
	fi
	if [[ $spec =~ -F\ ([^ ]+) ]]; then
		func=${BASH_REMATCH[1]}
		"$func" "$cmd" "${COMP_WORDS[COMP_CWORD]}" "${COMP_WORDS[COMP_CWORD-1]}" 2>&1
	fi
	printf '%s|%s|' "$line" "$func"
	printf '%s\n' "${COMPREPLY[@]}" | LC_ALL=C sort | head -30 | tr '\n' ' '
	echo
}
while IFS= read -r line; do
	comp "$line"
done <<'LINES'
ls --col
ls -
cd 
cd s
chmod 
chmod +x f
chown root:
kill -
kill -s 
ssh -
tar -x
tar -xf 
tar -tf arch.tar 
make 
make -
cp --
rm -
mkdir -
find . -
find . -type 
grep --
grep --color=
sed -
awk -
diff -
cat 
cat f
less 
man ls
man -
env 
nice 
sudo 
time 
which 
xargs -
head -
tail -
sort -
uniq -
wc -
touch 
ln -
gzip -
gzip 
gunzip 
bzip2 
xz 
unzip 
zip -
pwd -
umask -
type -
command -
complete -
export 
export HO
unset HOM
set -o 
shopt -s nocase
alias 
declare -
printf -
read -
trap 
ulimit -
cd ~
echo $HO
echo ${HO
python3 -
perl -
ruby -
curl --
wget --
rsync -
scp -
ping -
id -
groups 
chgrp 
du -
df -
mount -
ps -
pkill -
pgrep -
killall -
nohup 
watch -
openssl 
openssl s_
LINES
