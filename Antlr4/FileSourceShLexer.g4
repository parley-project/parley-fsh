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

	
	boolean parameterEndAhead() {
		 char nx =  (char)_input.LA(1);
		 if( nx == '}') {
			 return true;
		 }		 
	    return false;
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

	// a keyword stands alone: done-now and if.txt are words
	boolean atKeywordEnd() {
		int next = _input.LA(1);
		return next == org.antlr.v4.runtime.IntStream.EOF || " \t\r\n;|&()<>{}[]".indexOf(next) >= 0;
	}

	
}



PARAMETER_START: '${' ->pushMode(ParameterMode);

HERE_START:'<<';
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
DQ_STRING
    : '"' DQ_PART* '"'
    ;

fragment DQ_PART
    : ~["\\$] | '\\' . | '$(' CMD_PART* ')' | '$'
    ;

fragment CMD_PART
    : ~["'()\\] | '\\' . | '(' CMD_PART* ')' | '"' DQ_PART* '"' | '\'' ~['\\]* '\''
    ;

SQ_STRING
    : '\'' ( ~['\\] | '\\' . )* '\''
    ;

ESC: '\\' .;


WS: [ \t\r]+ ;



TRUE: 'true' {atKeywordEnd()}?;
FALSE: 'false' {atKeywordEnd()}?;
COMMENT: '/*' .*? '*/' -> skip;


LINE_COMMENT: '#'  ~[\r\n]* (EOF | NL) -> skip;



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
RX_EQUALITY:'=~';

NOT_EQ:'!='|'-ne';
// read as comparisons only in tests; elsewhere they are words (ls -lt). Before ARG_ID, which is as long.
TEST_OP:'-lt'|'-le'|'-gt'|'-ge';
MINUS:'-';
PIPE:'|';
AMP:'&';
TILDE:'~';
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

DOLLAR_LPAREN_LPAREN: '$((';
RPAREN_RPAREN:  '))';
LPAREN_LPAREN: '((';

NOT_CURLY: [ \t]|~[}];
DECLARE_A : 'declare' WS* '-' DECLARE_OP+;
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
