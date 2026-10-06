parser grammar FileSourceShParser;


options {
   tokenVocab = FileSourceShLexer;
   
}

script: SHEBANG? statement+ EOF;

	
conditionalStatement:
	  white* left=statement1 white* op=(OR|AND) white* right=statement1 white*
	| conditionalStatement white* op=(OR|AND) white* right=statement1 white*
	;
	
		
// a trailing & runs the statement in the background. (A separate rule that repeated the whole
// command before the & made the fast SLL parse fail on every command.)
statement:
	  white* statement1 (WS* bg=AMP)? WS* (NL|SEMI|EOF)
	| conditionalStatement  (NL|SEMI|EOF)
	;
	
statement1:
      ifStatement
    | mathStatement
    | whileStatement
    | forStatement
    | selectStatement
    | caseStatement
    | assignStatement
    | functionDefinition
    | until_statement
    | doStatement
    // after assignments and function definitions, which also start with a name (where commandStatement was)
    | pipeStatement
    | loop_controll_statement
    | declareAssociativeArrayStatement
    | boolean_statement
    | compareStatement
    | command_substitution
    | exprStatement
    | job_control_statement
    
    ;

loop_controll_statement: 
			  BREAK WS* NUMBER?
            | CONTINUE WS* NUMBER?
            ;

assignStatement: assignment WS*
		;
		
// the value is a word, like a command argument: x=sub-dir and y=$x$x are text, x=$((1+2)) is a number
assignment:		
      (LOCAL WS)? WS* id1=ID WS* EQ WS* arrayInitializer // Specific rule for array init
    | (LOCAL WS)? WS* id1=ID (WS* (associative_index | array_index))? WS* EQ WS* value=argument?
    ;

boolean: TRUE | FALSE;

id_star:ID STAR | STAR ID;

path_segment: TILDE 
		| AT
		| id_star
		| ID
        | DOT_DOT
        | DOT
        | STAR
        | QUESTION
        | string
        | MINUS
        | MINUS_MINUS
        | NUMBER
        | LOCAL        
        | COLON
		;

path_segment_list: path_segment +;

path:
      SLASH path_segment_list (SLASH path_segment_list)*   
    | path_segment_list (SLASH path_segment_list)*
    | SLASH        
    ;

argument_list: WS* (argument (WS+ argument)* WS*)?
	;


	
// A word: one or more parts with no whitespace between them, such as a$x, "$HOME"/x-y.txt or -la.
// Words are separated by whitespace, so the parser never has to guess where one argument ends.
argument: argumentPart+ ;

argumentPart:
      literal=(ID | NUMBER | ARG_ID | TEXT | SLASH | TILDE | AT | DOT | DOT_DOT | STAR | QUESTION
             | MINUS | MINUS_MINUS | PLUS | PERC | COLON | COMMA | EQ | LOCAL | TRUE | FALSE
             // keywords and [ ] ! are text in a word (echo done, echo [$w], ls [!a]*.txt);
             // they can't start a command, so loops, tests and ! pipelines are unaffected
             | IF | FI | THEN | ELSE | ELIF | FOR | SELECT | IN | WHILE | DONE | UNTIL | CASE | ESAC
             | DO | TIME | FUNCTION | CONTINUE | BREAK | LSQUARE | RSQUARE | NOT)
    | string
    | argVariable
    | parameter
    | mathExpression
    | arg_command_substitution
    | braceExpansion
    ;

// $name, $1, $? ... (a bare name is a literal part of the word); $name[index] indexes an array
argVariable: VARIABLE (associative_index | array_index)? ;
    
signed_number: (MINUS|PLUS|PERC)? NUMBER;    


// one alternative: with two that differ only at the end, the parser had to read the whole
// command before it could choose (seconds for a long path)
commandStatement:
      WS*	redirect1=redirect? WS* command (WS+ argument)* WS* (hereDocument WS*)? redirect2=redirect?
    ;
    
    
redirect: (redirect_one WS*)+;
 
 // the file descriptor in "2>file" is the argument before the operator (see FileSourceShVisitorImpl.parseRedirect);
 // an optional argument here made the parser read to the end of every command
 redirect_one: 
 		  redirectionOperator white* (args=argument WS*)?
		| file_address
		| redirectionOperator white* (args=argument WS*)? white* file_address
		;    

file_address:
        	fromId=NUMBER? REDIRECT_BOTH toId=NUMBER
         | fromId=NUMBER? REDIRECT_BOTH toId=MINUS
        ;



command: path
		| ID
		;


// also a single command or group: an alternative that repeated the command before the first |
// made the fast SLL parse fail on every command
pipeStatement:
     white* (TIME white*)? parg=ARG_ID? white* (NOT white*)? pipeableStatement (pipeOp pipeableStatement)* 
    ;
    
pipeableStatement:
		commandStatement
		| statement_group WS*  // a command takes the spaces before | itself; a group did not, so "{ ...; } | x" failed
		;
		    
pipeOp:
	PIPE white* AMP?
	;    

compareStatement:  LSQUARE WS* simpleCompare=compare WS* RSQUARE WS* statement?;

mathStatement:
     mathExpression
    | parameter
    
    ;

mathExpression:
      DOLLAR_LPAREN_LPAREN expression RPAREN_RPAREN
    | LPAREN_LPAREN expression RPAREN_RPAREN
    ;

boolean_statement: boolean;

compare : 
		  WS* compare_prime (';' WS*)?
        | WS* LSQUARE WS* compare_prime WS* RSQUARE
        | WS* LSQUARE WS* simpleCompare=compare WS* RSQUARE
        | WS* NOT notCompare=compare
        | left=compare WS* AND WS* right=compare
        | left=compare WS* OR WS*  right=compare
        ;

compare_prime: 
	  boolean
    | NUMBER
    | string
    | file_test
    | left=compare_prime WS* EQUALITY WS* right=compare_prime
    | left=compare_prime WS* NOT_EQ WS* right=compare_prime
    | left=compare_prime WS* LT_EQ WS* right=compare_prime
    | left=compare_prime WS* GT_EQ WS* right=compare_prime
    | left=compare_prime WS* LT WS* right=compare_prime
    | left=compare_prime WS* GT WS* right=compare_prime
    | left=compare_prime WS* RX_EQUALITY WS* regular_expression    
    | expression
    | commandStatement
    ;

// -f file, -d dir ... (the operator is an option, so [ and ] are not taken for one)
file_test: WS* op=ARG_ID WS+ target=argument WS*;

associative_index:
		(LSQUARE ID RSQUARE)
		| (LSQUARE index=string RSQUARE)
		;

regular_expression:	rx_pattern+ ;


expression:
      simpleTerm=term
    | variable postOp=(PLUS_PLUS|MINUS_MINUS)
    | preOp=(PLUS_PLUS|MINUS_MINUS) variable
    | variable op=PLUS_EQ expression
    | variable op=MINUS_ASSIGN expression
    | variable op=STAR_ASSIGN expression
    | variable op=DIV_ASSIGN expression
    | variable op=MOD_ASSIGN expression
    | expression op=(PLUS | MINUS| PERC) complexTerm=term
       ;


term:
      factor
    | term op=(STAR | DIVIDE | PERC | POW) factor
    ;


// case word in [(]pattern [| pattern]...) commands ;; ... esac  (on one line or several)
caseStatement:
       CASE WS+ subject=argument white+ IN white+ (caseClause white*)* ESAC
    ;



// the last clause may leave out ;;
caseClause:
        (LPAREN WS*)? patternList WS* RPAREN white* statement_block white* op=(SEMI_SEMI|SEMI_AMP|SEMI_SEMI_AMP)?
    ;


patternList:
       pattern (WS* PIPE WS* pattern)*
    ;

	
rx_pattern: 
	  ESC
    | RX_CHAR
    | HASH    
    | variable
    | string
    | TEXT  
    | ID
    | DOLLAR
    | NOT
    | regex    
    | STAR 
    | QUESTION
    | NUMBER
    | POS
    | char_class_list
    | '(' rx_pattern+ ')'
    ;

// a glob, written as a word
pattern: argument ;

char_class_list: char_class+;

char_class :char_class_a|char_class_b;
 
char_class_a: '[' char_class_b ']';
char_class_b: '[' not=('!'|'^')? char_class_body+ ']';

char_class_body:  POSIX_CHAR_CLASS|char_class_chars|char_class_range;

char_class_range: char_class_chars MINUS char_class_chars (MINUS char_class_chars)*; 

char_class_chars: (ESC|NUMBER|ID|DOT|QUESTION|STAR|TEXT);

regex: ID? (STAR|QUESTION|DOT|PLUS) ID? regex?;

	
factor:
      NUMBER
    | string
    | variable
    | parameter
    | LPAREN expression RPAREN
    | boolean
    ;

//2>&1

//2>&1
redirectionOperator:
//                  >       1>&2
       GT PIPE?
    |  REDIRECT_APPEND_OUT_2
    |  REDIRECT_APPEND_OUT

    | LT
    | REDIRECT_BOTH //>&word
    | REDIRECT_BOTH_2 //&>word
    | REDIRECT_READ_WRITE // <>
    | REDIRECT_INPUT_FROM_FID // <&
    ;



	

white: NL | WS;			

ifStatement:
	IF white* compare white* (SEMI|NL) white* THEN white* statement_block white* 
           (ELIF white* compare white* (SEMI|NL) white* THEN white* statement_block)*
        (white* ELSE white* statement_block)?
      white* FI white*
    ;

statement_block:
		 (white* statement_or_statement1 white*)*
		;


whileStatement:
      white* WHILE white* compare white* (';' white*)? doStatement
    ;

until_statement:
     white* UNTIL white* compare white*  doStatement
    ;

doStatement:
     white* DO white* statement* white* DONE
    ;

forStatement:
     white* FOR white* ID white* IN white* list white* SEMI? doStatement
    | white* FOR white* for_loop_control white* SEMI? doStatement
    ;

selectStatement:
     white* SELECT white* ID white* (IN white* path)? white* SEMI? white*  NL?white*  doStatement
    |white* SELECT white* ID white* (IN white* list)? white* SEMI? white*  NL?white*  doStatement
     ;

for_loop_control: LPAREN_LPAREN white* assignStatement white* SEMI white* for_compare white* SEMI white* expression white* RPAREN_RPAREN;

for_compare: compare;

variable:
        idOnly=ID ( associative_index | array_index)?
        |VARIABLE (associative_index | array_index)?

    ;

array_index:
		(LSQUARE index=expression RSQUARE)
		;




// <<- is converted by preprocessor to <<
hereDocument: HERE_START WS* ID;

functionDefinition:
     white* (FUNCTION white*)? ID white* (LPAREN white* RPAREN white*)? compoundCommand
    
    ;

string : DQ_STRING | SQ_STRING | ESC;

arrayInitializer:
     LPAREN argument_list RPAREN
    ;

// ends at a newline or ;, as in bash
list: 
	  argument (WS+ argument)* WS*
    ;

statement_or_statement1: (statement|statement1);

statement_group: redirect1=redirect? statement_group1 redirect2=redirect? 
    	;
		
statement_group1
 		: redirect1=redirect?  LCURLY white* statement_or_statement1* white* RCURLY redirect1=redirect?
        | redirect1=redirect?  LPAREN white* statement_or_statement1* white* RPAREN redirect1=redirect?
		;



compoundCommand:
          redirect1=redirect?  LCURLY white* statement* white* RCURLY redirect1=redirect?
        | redirect1=redirect? LPAREN white* statement* white* RPAREN redirect1=redirect?
        
        ;

command_substitution:
			'$(' ~')'* ')'
			| '`' ~'`'* '`'
			;

arg_command_substitution:
			'$(' ~')'* ')'
			| '`' ~'`'* '`'
			;

exprStatement: expr;

expr: EXPR_START EXPR_BODY (EXPR_END|EOF);

parameter: PARAMETER_START PARAMETER_BODY PARAMETER_END;
		
parameter1:
     (NOT|PIPE)? ID parameter_index?  parameter_body 
    |  NOT? (TEXT|AT|AMP|STAR) parameter_body 
    |  NOT? expression parameter_index?  parameter_body 
    
    ;


parameter_index:

		 LSQUARE (TEXT|AT) RSQUARE
		
		| associative_index
		| array_index
		;

parameter_body:
      pbody
    | '#' pattern_string DIVIDE replacement_string
    ;

pattern_string: ~DIVIDE*;

replacement_string: ~RCURLY* ;

pbody: ~RCURLY*;

// New rule to support 'declare -A my_array' and 'declare -A my_array=([key1]=value1 [key2]=value2)'
declareAssociativeArrayStatement:
     white* DECLARE_A WS* id1=ID (WS* EQ WS* associativeArrayInitializer)? 
    ;

associativeArrayInitializer:
     white* LPAREN white* (associativeArrayElement white*) * RPAREN    
    ;


// text before and after the braces is part of the word (see Argument)
braceExpansion: LCURLY (braceRange|braceArgList) RCURLY
	;
	
braceArgList:associativeArrayValue (COMMA associativeArrayValue)*;
braceRange: start=associativeArrayValue DOT_DOT end=associativeArrayValue (DOT_DOT incr=associativeArrayValue)?;

associativeArrayElement:
    white* LSQUARE key=argument RSQUARE WS* EQ WS* value=argument white*
    ;

associativeArrayValue:
     string
    | NUMBER
    | boolean
    | variable
    | mathExpression
    | parameter
    ;

job_control_statement: cmd=ID (WS+ argument)* (WS+ jobspec)* WS*;
jobspec:(signed_number|PERC_PERC|PERC_PLUS|PERC_MINUS|PERC_QUESTION ID?);

