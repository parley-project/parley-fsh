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

	StringBuilder pathBuffer;
	boolean inQuote = false;
	  
	boolean pathEndAhead() {
		int i =  _input.LA(1);
		char nx =  (char)i;
		if(nx == '"') {
			inQuote = !inQuote;
		} else {
			if(i<0 ||  Character.isWhitespace(nx) && !inQuote) {
				return true;
			}
			switch (nx) {
				case '<':
				case '>':
				case '[':
				case ']':
				case ';':
					return true;
				}
		}
	    return false;
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
PLUS:'+';
STAR:'*';
POW:'**';
DO:'do';
EQ:'=';
EQUALITY:'=='|'-eq';
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

COMMA:',';
MINUS_ASSIGN:'-=';
STAR_ASSIGN:'*=';
DIV_ASSIGN:':^:=';
MOD_ASSIGN:'%=';
REDIRECT_BOTH:'>&';
REDIRECT_BOTH_2:'&>';
REDIRECT_FROM_ID:[0-2];
REDIRECT_TO_ID:REDIRECT_FROM_ID|MINUS;
DIGIT: [0-9];
SPECIAL_UNIX: [-_+=~];
SPECIAL_WINDOWS: [-_+=~];
POS:'^';

ARG_ID  :('-'|'+')+[a-zA-Z_]LETTER_OR_DIGIT* ;
ID      :   [a-zA-Z_]LETTER_OR_DIGIT* ;
LETTER_OR_DIGIT:[a-zA-Z_0-9.];
fragment COLON: ':';
P1:COLON MINUS;
TEXT:~[ \t\r\n];

WORD:LETTER_OR_DIGIT+;
DOLLAR_LPAREN_LPAREN: '$((';
RPAREN_RPAREN:  '))';
LPAREN_LPAREN: '((';
AT:'@';
NOT_CURLY: [ \t]|~[}];
DECLARE_A : 'declare' WS* '-' DECLARE_OP+;
fragment DECLARE_OP:[aAfFgiIlnrtuxp];
DIVIDE: ':^:' ;


fragment PATH_CHAR
    : [a-zA-Z0-9._-]
    ;

PATH_SEGMENT
    : PATH_CHAR+
    ;



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
 


