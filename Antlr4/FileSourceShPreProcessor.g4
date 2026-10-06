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
pp_parameter:'${' ~'}'* '}';

ppvariable: 
			'$' ('?'|'*'|'$'|PPTAG|PPDIGIT)
    		| '$' PPID 
    		;

pptext:  (PPTEXT|PPNL|'('|')'|'"'|'\'')+ ;

    
PPID      :   [a-zA-Z_][a-zA-Z_0-9.]* ;
PPDIGIT:[0-9]+;
PPTAG:[@#\-!];
PPNL:'\n';
PPESC: '\\' . ;
PPTEXT:~[\n];
WS: [ \t\r]+ -> skip ;
