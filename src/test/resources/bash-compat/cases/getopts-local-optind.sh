# a function with local OPTIND that calls itself while in the middle of -xyz
f()
{
	typeset OPTIND=1
	typeset opt
	while getopts ":abcxyz" opt
	do
		echo opt: "$opt"
		if [[ $opt = y ]]; then f -abc ; fi
	done
}
f -xyz
