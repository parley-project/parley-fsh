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
	  
  	boolean exprEndAhead() {
		 char nx =  (char)_input.LA(1);
		 if( nx == ';' || nx == '\n'|| nx == EOF) {
			 return true;
		 }		 
	    return false;
	 }
	
}



PARAMETER_START: '${' ->pushMode(ParameterMode);

EXPR_START: 'expr' ->pushMode(ExprMode);

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

DQ_STRING
    : '"' ( ~["\\] | '\\' . )* '"'
    ;

SQ_STRING
    : '\'' ( ~['\\] | '\\' . )* '\''
    ;

ESC: '\\' .;


WS: [ \t\r]+ ;



TRUE: 'true';
FALSE: 'false';
COMMENT: '/*' .*? '*/' -> skip;


LINE_COMMENT: '#'  ~[\r\n]* (EOF | NL) -> skip;



SHEBANG: '#!' ~[\r\n]* NL;
LOCAL:'local';
LCURLY:'{';
RCURLY:'}';
FUNCTION:'function';
CRETURN:'\r';
SPACE:' ';
TAB:'\t';

QUOTE:'\'';
BACKQUOTE:'`';
CONTINUE:'continue';
BREAK:'break';
FOR:'for';
SELECT: 'select';
IN:'in';
WHILE:'while';
DONE:'done';

UNTIL:'until';
IF:'if';
FI:'fi';
THEN:'then';
ELSE:'else';
ELIF:'elif';
SLASH:'/';
BACKSLASH:'\\';
CASE:'case';
ESAC:'esac';

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
DO:'do';
EQ:'=';
EQUALITY:'=='|'-eq';
RX_EQUALITY:'=~';

NOT_EQ:'!='|'-ne';
MINUS:'-';
PIPE:'|';
AMP:'&';
TILDE:'~';
QUESTION:'?';
TIME:'time';
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

ARG_ID  :~[a-zA-Z0-9]('-'|'+')+[a-zA-Z_]LETTER_OR_DIGIT* ;
//ARG_ID  :('-'|'+')+[a-zA-Z_]LETTER_OR_DIGIT* ;
ID      :   [a-zA-Z_]LETTER_OR_DIGIT* ;
LETTER_OR_DIGIT:[a-zA-Z_0-9];
fragment COLON: ':';
P1:COLON MINUS;
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

mode ExprMode;
EXPR_BODY: ({!exprEndAhead()}? . )+ ;
 
EXPR_END: {exprEndAhead()}? (';'|'\n'|EOF) -> popMode;
 


