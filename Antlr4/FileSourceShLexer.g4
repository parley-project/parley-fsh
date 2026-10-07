lexer grammar FileSourceShLexer;

@members {
  String hereStart = null;
	
  boolean hereEndAhead() {
    for (int i = 1; i <= hereStart.length(); i++) {
      if (hereStart.charAt(i - 1) != _input.LA(i)) {
        return false;
      }
    }
    return true;
  }

	
	// the } that ends ${...}: not one that closes a ${ inside it (${a:-${b}})
	boolean parameterEndAhead() {
		if( _input.LA(1) != '}') {
			return false;
		}
		int start = _tokenStartCharIndex;
		int stop = _input.index()-1;
		if( stop < start ) {
			return true;
		}
		String text = _input.getText(org.antlr.v4.runtime.misc.Interval.of(start, stop));
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if( c == '\\' ) {
				i++;
			} else if( c == '$' && i+1 < text.length() && text.charAt(i+1) == '{' ) {
				depth++;
				i++;
			} else if( c == '}' && depth > 0 ) {
				depth--;
			}
		}
		return depth == 0;
	}
	  
	// an option like -la only starts a word: at the start of input or after whitespace,
	// so x-y in $((x-y)) stays x MINUS y
	boolean atWordStart() {
		int prev = _input.LA(-1);
		return prev == ' ' || prev == '\t' || prev == '\n' || prev == '\r' || prev == org.antlr.v4.runtime.IntStream.EOF;
	}

	// the file descriptor of a redirect starts a word ((( is not one: $((3>2)))
	boolean atRedirectStart() {
		int prev = _input.LA(-1);
		return prev == org.antlr.v4.runtime.IntStream.EOF || " \t\r\n;|&{".indexOf(prev) >= 0;
	}

	boolean redirectAhead() {
		int next = _input.LA(1);
		return next == '<' || next == '>';
	}

	boolean atCommentStart() {
		int prev = _input.LA(-1);
		return prev == org.antlr.v4.runtime.IntStream.EOF || " \t\r\n;&|(){}".indexOf(prev) >= 0;
	}

	// a keyword stands alone: done-now and if.txt are words
	boolean atKeywordEnd() {
		int next = _input.LA(1);
		return next == org.antlr.v4.runtime.IntStream.EOF || " \t\r\n;|&()<>{}[]".indexOf(next) >= 0;
	}

	
}



PARAMETER_START: '${' ->pushMode(ParameterMode);

HERE_START:'<<';
// cmd <<< word: word and a newline are the input
HERE_STRING:'<<<';
HERE_START_RM_TABS:'<<-';


SEMI:';';
SEMI_SEMI:';;';
SEMI_AMP:';&';
SEMI_SEMI_AMP:';;&';
DOLLAR_PAREM:'$(';
HASH:'#';

NL:  '\n';


LT:'<';
LT_EQ:'<=';
GT:'>';
GT_EQ:'>=';
NOT: '!';
AND: '&&';
OR:  '||';
ESC_AND: '\\&&';
ESC_OR:  '\\||';



// exec {fd}>file: the shell picks a descriptor and puts its number in fd
VARFD: {atRedirectStart()}? '{' [a-zA-Z_] [a-zA-Z_0-9]* '}' {redirectAhead()}? ;

// the file descriptor of a redirect: digits right before < or >, as in 2>file, 2>&1, 3<file.
// With a space (echo 2 > f) the digits are a word.
IO_NUMBER: {atRedirectStart()}? [0-9]+ {redirectAhead()}? ;

NUMBER :
     INTEGER
    | DECIMAL
    ;

fragment EXPONENT : ('e'|'E') ('+'|'-') ? INTEGER+;


VARIABLE
    : DOLLAR ID
    | DOLLAR (DOLLAR| STAR|QUESTION|[@#\-!]|DIGIT+)

    ;



INTEGER
    : [0-9]+ ;

DECIMAL
    : INTEGER DOT INTEGER  EXPONENT?;

// $( ) inside a double-quoted string may contain quotes and parentheses: "$(echo "x")"
// "...", and $"..." (a string to translate; there is no catalog, so it is the same text)
DQ_STRING
    : '$'? '"' DQ_PART* '"'
    ;

// $[ expression ]: the old form of $(( expression ))
DOLLAR_BRACKET: '$[' ~[\]]* ']' ;

fragment DQ_PART
    : ~["\\$] | '\\' . | '$(' CMD_PART* ')' | '${' PARAM_PART* '}' | '$'
    ;

// inside ${ } in double quotes: quotes and ${ } nest ("${s#"${s%%x}"}")
fragment PARAM_PART
    : ~["'{}\\] | '\\' . | '{' PARAM_PART* '}' | '"' DQ_PART* '"' | '\'' ~[']* '\''
    ;

fragment CMD_PART
    : ~["'()\\] | '\\' . | '(' CMD_PART* ')' | '"' DQ_PART* '"' | '\'' ~[']* '\''
    ;

// $'a\tb': quoted, with backslash escapes as in C
ANSI_STRING
    : '$\'' ( ~['\\] | '\\' . )* '\''
    ;

// <(cmd): the command's output, as a file name
PROC_SUBST
    : '<(' CMD_PART* ')'
    ;

// >(cmd): a file name; what is written to it is the command's input
PROC_SUBST_OUT
    : '>(' CMD_PART* ')'
    ;

// everything between single quotes is as written, backslashes too ('a\' is a\), as in bash
SQ_STRING
    : '\'' ~[']* '\''
    ;

// backslash-newline joins lines (echo a \<newline> b is echo a b)
LINE_CONTINUATION: '\\' '\r'? '\n' -> skip;
ESC: '\\' .;


WS: [ \t\r]+ ;



TRUE: 'true' {atKeywordEnd()}?;
FALSE: 'false' {atKeywordEnd()}?;
// (there are no /* */ comments, as in bash: ls /*/bin and d/*.txt are patterns)


// # starts a comment only at the start of a word (a#b and $# are not comments); the newline after
// it is kept, so the next line stays a separate command
LINE_COMMENT: {atCommentStart()}? '#' ~[\r\n]* -> skip;



SHEBANG: '#!' ~[\r\n]* [\r\n];
LOCAL: 'local' {atKeywordEnd()}?;
LCURLY:'{';
RCURLY:'}';
FUNCTION: 'function' {atKeywordEnd()}?;
CRETURN:'\r';
SPACE:' ';
TAB:'\t';

QUOTE:'\'';
BACKQUOTE:'`';
CONTINUE: 'continue' {atKeywordEnd()}?;
BREAK: 'break' {atKeywordEnd()}?;
FOR: 'for' {atKeywordEnd()}?;
SELECT: 'select' {atKeywordEnd()}?;
IN: 'in' {atKeywordEnd()}?;
WHILE: 'while' {atKeywordEnd()}?;
DONE: 'done' {atKeywordEnd()}?;

UNTIL: 'until' {atKeywordEnd()}?;
IF: 'if' {atKeywordEnd()}?;
FI: 'fi' {atKeywordEnd()}?;
THEN: 'then' {atKeywordEnd()}?;
ELSE: 'else' {atKeywordEnd()}?;
ELIF: 'elif' {atKeywordEnd()}?;
SLASH:'/';
BACKSLASH:'\\';
CASE: 'case' {atKeywordEnd()}?;
ESAC: 'esac' {atKeywordEnd()}?;

DOLLAR:'$';
PLUS_PLUS:'++';
MINUS_MINUS:'--';
PLUS_EQ:'+=';
DOT:'.';
DOT_DOT:'..';
PERC:'%';
//JOBSPEC: '%'[0-9]+;
PLUS:'+';
STAR:'*';
POW:'**';
DO: 'do' {atKeywordEnd()}?;
EQ:'=';
EQUALITY:'=='|'-eq';
// only as a word of its own: x=~ is x = ~ (the home directory)
RX_EQUALITY: {atWordStart()}? '=~';

NOT_EQ:'!='|'-ne';
// read as comparisons only in tests; elsewhere they are words (ls -lt). Before ARG_ID, which is as long.
TEST_OP:'-lt'|'-le'|'-gt'|'-ge';
MINUS:'-';
PIPE:'|';
AMP:'&';
// ~ is $HOME, ~+ $PWD and ~- $OLDPWD (at the start of a word)
TILDE:'~' [+\-]?;
QUESTION:'?';
TIME: 'time' {atKeywordEnd()}?;
LPAREN:'(';
RPAREN:')';
LSQUARE:'[';
RSQUARE:']';

REDIRECT_APPEND_OUT_2 : '&>>';
REDIRECT_APPEND_OUT : '>>';
REDIRECT_READ_WRITE : '<>';
REDIRECT_BOTH:'>&';
REDIRECT_BOTH_2:'&>';
REDIRECT_INPUT_FROM_FID:'<&';

COMMA:',';
MINUS_ASSIGN:'-=';
STAR_ASSIGN:'*=';
DIV_ASSIGN:':^:=';
MOD_ASSIGN:'%=';
DIGIT: [0-9];
SPECIAL_UNIX: [-_+=~];
SPECIAL_WINDOWS: [-_+=~];
POS:'^';


PERC_PERC:'%%';
PERC_MINUS:'%-';
PERC_PLUS:'%+';
PERC_QUESTION:'%?';

// was ~[a-zA-Z0-9]('-'|'+')+..., which took the character before the dash into the token
// (" -la", "/-Volumes"), so a path with "/-" could not be parsed
ARG_ID  : {atWordStart()}? ('-'|'+')+[a-zA-Z_]LETTER_OR_DIGIT* ;
ID      :   [a-zA-Z_]LETTER_OR_DIGIT* ;
LETTER_OR_DIGIT:[a-zA-Z_0-9];
COLON: ':';
AT:'@';
TEXT:~[ \t\r\n];

// extended patterns (shopt -s extglob in bash): ?(a|b) *(a|b) +(a|b) @(a|b) !(a|b), with nesting.
// Part of a word; outside a pattern they are syntax errors in bash, so they are always read.
EXTGLOB: [?*+@!] '(' EXTGLOB_BODY ')' ;
fragment EXTGLOB_BODY: ( ~[()] | '(' EXTGLOB_BODY ')' )* ;

// [[ expression ]]: DoubleBracket evaluates the text (the words are not split or globbed)
DBL_TEST: {atCommentStart()}? '[[' [ \t\n] .*? [ \t\n] ']]' ;

// $(( expression )) and (( expression )): Arithmetic evaluates the text. Parentheses inside nest.
// (The three tokens after them are left for text that is not balanced.)
ARITH_EXPANSION: '$((' ARITH_BODY '))';
ARITH_COMMAND: '((' ARITH_BODY '))';
fragment ARITH_BODY: ( ~[()] | '(' ARITH_BODY ')' )* ;

DOLLAR_LPAREN_LPAREN: '$((';
// (no )) token: ) ) closes $(( or (( that is not arithmetic, and a=($(cmd)) is two )s)
LPAREN_LPAREN: '((';

NOT_CURLY: [ \t]|~[}];
// declare -l x, declare +l x (+ takes the attribute away)
DECLARE_A : 'declare' WS* [-+] DECLARE_OP+;
fragment DECLARE_OP:[aAfFgiIlnrtuxp];
DIVIDE: ':^:' ;
RX_CHAR:[!@#$%^&*()_+~];
POSIX_CHAR_CLASS: 
	':' '^'? ('alnum'|'alpha'|'ascii'|'blank'|'cntrl'|'digit'|'graph'|'lower'|'print'|'punct'|'space'|'upper'|'word'|'xdigit') ':'
	;

CHAR_CLASS: [.];

	
mode ParameterMode;


PARAMETER_BODY
 : ({!parameterEndAhead()}? . )+
 ;
 
PARAMETER_END
 : {parameterEndAhead()}? '}' -> popMode
 ;
