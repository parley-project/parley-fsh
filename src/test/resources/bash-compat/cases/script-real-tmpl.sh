name=World; count=3; lang=bash
tpl='Hello {{name}}! You have {{count}} new {{lang}} tips. Missing: [{{nope}}].'
out=$tpl
while [[ $out =~ \{\{([a-z]+)\}\} ]]; do key=${BASH_REMATCH[1]}; out=${out//"{{$key}}"/${!key}}; done
echo "$out"
render() { local s=$1 v; for v in "${@:2}"; do s=${s//"%$v%"/${!v}}; done; echo "$s"; }
render "%name% uses %lang%" name lang
