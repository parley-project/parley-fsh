[[ ab =~ (a)(x)?(b) ]] && echo "${#BASH_REMATCH[@]} [${BASH_REMATCH[2]}] ${BASH_REMATCH[3]}"
