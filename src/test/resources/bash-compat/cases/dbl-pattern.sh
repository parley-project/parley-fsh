[[ abc == a* ]] && echo glob; [[ abc == b* ]] || echo noglob; [[ abc == "a*" ]] || echo literal
