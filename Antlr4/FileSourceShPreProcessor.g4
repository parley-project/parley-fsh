grammar FileSourceShPreProcessor;



ppcode: (ppcommand|ppexpr|ppvariable|pptext|pp_parameter|ppescape|PPID)* EOF;

// \$ \` \" \\ ... : the escaped character is never an expansion
ppescape: PPESC;

ppexpr: '$((' pp_nested* ')' ')';
ppcommand: pp_backtick_command | pp_dollar_command;
		

pp_backtick_command : '`' ~'`'* '`';
pp_dollar_command:'$(' pp_nested* ')';

// the text of $( ) or $(( )): parentheses nest, and a ) in quotes does not close it
pp_nested
    : ~('$((' | '$(' | '(' | ')' | '"' | '\'')
    | ('$(' | '(') pp_nested* ')'
    | '$((' pp_nested* ')' ')'
    | '"' pp_dq* '"'
    | '\'' ~'\''* '\''
    ;

pp_dq
    : ~('"' | '$(' | '$((')
    | '$(' pp_nested* ')'
    | '$((' pp_nested* ')' ')'
    ;
// ${ } nests: ${a:-${b}}
pp_parameter:'${' pp_param* '}';
// a } in quotes is text: "${y:-"}"}"
pp_param: '"' pp_param_dq* '"' | '\'' ~'\''* '\'' | ~('${' | '{' | '}') | ('${' | '{') pp_param* '}' ;
pp_param_dq: ~('"' | '${') | '${' pp_param* '}' ;

ppvariable: 
			'$' ('?'|'*'|'$'|PPTAG|PPDIGIT)
    		| '$' PPID 
    		;

// digits and @ # - ! not after a $ are text ("5! = $(f)": two in a row stopped the expansion)
pptext:  (PPTEXT|PPNL|PPDIGIT|PPTAG|'('|')'|'"'|'\''|'{'|'}')+ ;

    
// no dot: "$f.txt" is $f then .txt
PPID      :   [a-zA-Z_][a-zA-Z_0-9]* ;
PPDIGIT:[0-9]+;
PPTAG:[@#\-!];
PPNL:'\n';
PPESC: '\\' . ;
PPTEXT:~[\n];
WS: [ \t\r]+ -> skip ;
