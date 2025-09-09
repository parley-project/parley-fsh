parser grammar FileSourceShParser;


options {
   tokenVocab = FileSourceShLexer;
   
}

script: SHEBANG? statement+ EOF;

conditionalStatement
	: white* left=statement1 white* op=(OR|AND) white* right=statement1 white*
	| conditionalStatement white* op=(OR|AND) white* right=statement1 white*
	;
	
		
statement
	: white* statement1 WS* (NL|SEMI|EOF)
	| conditionalStatement  (NL|SEMI|EOF)
	;
	
statement1
    : backgroundCommand
    | ifStatement
    | mathStatement
    | pipeStatement
    | whileStatement
    | forStatement
    | selectStatement
    | caseStatement
    | assignStatement
    | functionDefinition
    | until_statement
    | doStatement
    | commandStatement
    | loop_controll_statement
    | declareAssociativeArrayStatement
    | boolean_statement
    | compareStatement
    | statement_group // Includes parenthesized groups
    | command_substitution
    | exprStatement
    
    
    ;

backgroundCommand:
				statement_group AMP
				| pipeStatement AMP
				| commandStatement AMP
				;

loop_controll_statement
			: BREAK WS* NUMBER?
            | CONTINUE WS* NUMBER?
            ;

assignStatement: assignment WS?
		;
		
assignment		
    : (LOCAL WS)? WS? id1=ID WS? EQ WS? arrayInitializer // Specific rule for array init
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? command_substitution
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? boolean
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? string    
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? variable
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? expression
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? mathExpression
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? parameter
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? list // Could be single element
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? id2=ID
    | (LOCAL WS)? WS? id1=ID (WS? (associative_index | array_index))? WS? EQ WS? path
    ;

boolean: TRUE | FALSE;
path_segment: 
		  TILDE 
		//| variable
        | DOT_DOT
        | DOT
        | STAR
        | QUESTION
        | string
        | MINUS
        | MINUS_MINUS
        | NUMBER
        | ID
		;


path
    : SLASH path_segment (SLASH path_segment)*   # absolutePath
    | path_segment (SLASH path_segment?)*        # relativePath
    ;

argument_list: (argument WS*)*
	;
	
argument
    :  arg_command_substitution
    | signed_number
    | NUMBER    
   	| TEXT
    | string
    | ARG_ID     
    | assignStatement            
    | mathExpression
    | parameter
    | operator
	| path	
	| ID
	| variable
    ;
    
signed_number: (MINUS|PLUS)? NUMBER;    

operator: MINUS|PLUS|DIVIDE|PERC|STAR
		|MINUS_MINUS|PLUS_PLUS|EQUALITY|NOT_EQ
		| MINUS_ASSIGN
		| STAR_ASSIGN
		| DIV_ASSIGN
		| MOD_ASSIGN
		| ESC LT
		|LT_EQ
		|ESC GT
		|GT_EQ
		|NOT
		|ESC_AND
		|ESC_OR
		;

commandStatement
    : WS*	redirect1=redirect? WS* command WS* (argument WS*)* hereDocument WS* redirect2=redirect? 
    | WS*	redirect1=redirect? WS* command WS* (argument WS*)* redirect2=redirect?     
    ;
    
redirect 
		: redirectionOperator white* (path | ID)
		| file_address
		| (redirectionOperator white* (path | ID)) white* file_address
		;    

file_address:
        	fromId=NUMBER? REDIRECT_BOTH toId=NUMBER
         | fromId=NUMBER? REDIRECT_BOTH toId=MINUS
        ;



command: path
		| ID
		;


pipeStatement
    : white* (TIME white*)? parg=ARG_ID? white* (NOT white*)? pipeableStatement (pipeOp pipeableStatement)+ 
    ;
    
pipeableStatement:
		commandStatement
		| statement_group
		;
		    
pipeOp:
	PIPE white* AMP?
	;    

compareStatement:  LSQUARE simpleCompare=compare RSQUARE statement?;

mathStatement
    : mathExpression
    | parameter
    
    ;

mathExpression
    : DOLLAR_LPAREN_LPAREN expression RPAREN_RPAREN
    | LPAREN_LPAREN expression RPAREN_RPAREN
    ;

boolean_statement: boolean;

compare : WS? compare_prime
        | WS? LSQUARE WS? compare_prime WS? RSQUARE
        | WS? LSQUARE WS? simpleCompare=compare WS? RSQUARE
        | WS? NOT notCompare=compare
        | left=compare WS? AND WS? right=compare
        | left=compare WS? OR WS?  right=compare
        ;

compare_prime
    : boolean
    | NUMBER
    | string
    | file_test
    | left=compare_prime WS? EQUALITY WS? right=compare_prime    
    | left=compare_prime WS? NOT_EQ WS? right=compare_prime
    | left=compare_prime WS? LT_EQ WS? right=compare_prime
    | left=compare_prime WS? GT_EQ WS? right=compare_prime
    | left=compare_prime WS? LT WS? right=compare_prime
    | left=compare_prime WS? GT WS? right=compare_prime
    | expression
    ;

file_test: WS* op=argument WS* target=argument WS*;

associative_index:
		(LSQUARE ID RSQUARE)
		| (LSQUARE index=string RSQUARE)
		;

expression
    :
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


term
    : factor
    | term op=(STAR | DIVIDE | PERC | POW) factor
    ;


caseStatement
    :   CASE WS* expression WS* IN NL caseClause+ ESAC
    
    ;



caseClause
    :   patternList white* RPAREN white* statement_block white* op=(SEMI_SEMI|SEMI_AMP|SEMI_SEMI_AMP) white

    ;


patternList
    :   WS* pattern (white* PIPE white* pattern)*
    ;

pattern
    :   ID
    | regex
    | expression
    | STAR 
    
    ;

regex: ID? (STAR|QUESTION|DOT) ID? regex?;

//regex: LETTER_OR_DIGIT? (STAR|QUESTION) LETTER_OR_DIGIT? 	;
	
factor
    : NUMBER
    | string
    | variable
    | parameter
    | LPAREN expression RPAREN
    | boolean
    ;

//2>&1

//2>&1
redirectionOperator
//                  >       1>&2
    : GT PIPE?
    |  REDIRECT_APPEND_OUT_2
    |  REDIRECT_APPEND_OUT

    | LT
    | REDIRECT_BOTH //>&word
    | REDIRECT_BOTH_2 //&>word
    ;



	

white: NL | WS;			

ifStatement
    : IF white* compare white* (SEMI|NL) white* THEN white* statement_block white* 
           (ELIF white* compare white* (SEMI|NL) white* THEN white* statement_block)*
        (white* ELSE white* statement_block)?
      white* FI white*
    ;

statement_block
		: (white* statement_or_statement1 white*)*
		;


whileStatement
    :  white* WHILE white* compare white* doStatement
    ;

until_statement
    : white* UNTIL white* compare white*  doStatement
    ;

doStatement
    : white* DO white* statement* white* DONE
    ;

forStatement
    : white* FOR white* ID white* IN white* list white* SEMI? doStatement
    | white* FOR white* for_loop_control doStatement
    ;

selectStatement
    :white* SELECT white* ID white* (IN white* path)? white* SEMI? white*  NL?white*  doStatement
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

functionDefinition
    : white* (FUNCTION white*)? ID white* (LPAREN white* RPAREN white*)? compoundCommand
    
    ;

string : DQ_STRING | SQ_STRING | ESC;

arrayInitializer
    : LPAREN argument_list RPAREN
    ;

list
    : (argument white*)+
    | white* LSQUARE white* argument white* RSQUARE white*
    ;

statement_or_statement1: (statement|statement1);

statement_group: redirect1=redirect? statement_group1 redirect2=redirect? 
    	;
		
statement_group1
 		: redirect1=redirect?  LCURLY white* statement_or_statement1* white* RCURLY redirect1=redirect?
        | redirect1=redirect?  LPAREN white* statement_or_statement1* white* RPAREN redirect1=redirect?
		;



compoundCommand
        : redirect1=redirect?  LCURLY white* statement* white* RCURLY redirect1=redirect?
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
    |  NOT? (TEXT|AMP|STAR) parameter_body 
    |  NOT? expression parameter_index?  parameter_body 
    
    ;


parameter_index :

		 LSQUARE TEXT RSQUARE
		
		| associative_index
		| array_index
		;

parameter_body
    : pbody
    | '#' pattern_string DIVIDE replacement_string
    ;

pattern_string : ~DIVIDE*;

replacement_string : ~RCURLY* ;

pbody: ~RCURLY*;

// New rule to support 'declare -A my_array' and 'declare -A my_array=([key1]=value1 [key2]=value2)'
declareAssociativeArrayStatement
    : white* DECLARE_A WS* id1=ID (WS* EQ WS* associativeArrayInitializer)? 
    ;

associativeArrayInitializer
    : white* LPAREN white* (associativeArrayElement white*) * RPAREN
    ;

associativeArrayElement
    :white* LSQUARE key=argument RSQUARE WS* EQ WS* value=argument white*
    ;

associativeArrayValue
    : string
    | NUMBER
    | boolean
    | variable
    | mathExpression
    | parameter
    ;

