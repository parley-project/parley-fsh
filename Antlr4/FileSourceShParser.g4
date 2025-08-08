parser grammar FileSourceShParser;


options {
   tokenVocab = FileSourceShLexer;
   
}

script: SHEBANG? statement+ EOF;

	
statement
    : stmt_only=statement SEMI
    | backgroundCommand
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
    | NL
    | left=statement op=OR  right=statement
    | left=statement op=AND right=statement
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

loop_controll_statement: BREAK NUMBER?
            | CONTINUE NUMBER?
            ;

assignStatement
    : LOCAL? id1=ID EQ arrayInitializer // Specific rule for array init
    | LOCAL? id1=ID (associative_index | array_index)? EQ command_substitution
    | LOCAL? id1=ID (associative_index | array_index)? EQ boolean
    | LOCAL? id1=ID (associative_index | array_index)? EQ string
    | LOCAL? id1=ID (associative_index | array_index)? EQ path
    | LOCAL? id1=ID (associative_index | array_index)? EQ variable
    | LOCAL? id1=ID (associative_index | array_index)? EQ expression
    | LOCAL? id1=ID (associative_index | array_index)? EQ mathExpression
    | LOCAL? id1=ID (associative_index | array_index)? EQ parameter
    | LOCAL? id1=ID (associative_index | array_index)? EQ list // Could be single element
    | LOCAL? id1=ID (associative_index | array_index)? EQ id2=ID
    ;

boolean: TRUE | FALSE;
path: path_segment? PATH_START PATH_BODY  ;

argument
    :  arg_command_substitution
    | path
   	| TEXT
    | string
    | ARG_ID
    | ID 
    | assignStatement
    | variable
    | NUMBER    
    | mathExpression
    | parameter
    | operator

    ;

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
    :	redirect1=redirect? command (argument)* hereDocument redirect2=redirect? CMD_TERMINATOR? 
    | 	redirect1=redirect? command (argument)* redirect2=redirect? CMD_TERMINATOR?    
    ;
    
redirect 
		: (redirectionOperator (path | ID))
		| file_address
		| (redirectionOperator (path | ID)) file_address
		;    

file_address:
        	fromId=NUMBER? REDIRECT_BOTH toId=NUMBER
         | fromId=NUMBER? REDIRECT_BOTH toId=MINUS
        ;



command
    : path
    | ID
    ;


pipeStatement
    : TIME? parg=ARG_ID? NOT? pipeableStatement (pipeOp pipeableStatement)+ 
    ;
    
pipeableStatement:
		commandStatement
		| statement_group
		;
		    
pipeOp:
	PIPE AMP?
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

compare : compare_prime
        | LSQUARE compare_prime RSQUARE
        | LSQUARE simpleCompare=compare RSQUARE
        | NOT notCompare=compare
        | left=compare AND right=compare
        | left=compare OR right=compare
        ;

compare_prime
    : boolean
    | NUMBER
    | string
    | file_test
    | left=compare_prime EQUALITY right=compare_prime    
    | left=compare_prime NOT_EQ right=compare_prime
    | left=compare_prime LT_EQ right=compare_prime
    | left=compare_prime GT_EQ right=compare_prime
    | left=compare_prime LT right=compare_prime
    | left=compare_prime GT right=compare_prime
    | expression
    ;

file_test: op=argument target=argument;

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
    :   CASE expression IN NL caseClause+ ESAC
    
    ;



caseClause
    :   patternList NL? RPAREN NL? statement_block NL? op=';;' NL?
    |   patternList NL? RPAREN NL? statement_block NL? op=';&' NL?
    |   patternList NL? RPAREN NL? statement_block NL? op=';;&' NL?

    ;


patternList
    :   pattern (PIPE pattern)*
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



	

			
path_segment: 
		  TILDE 
		| ID
		| variable
        | DOT_DOT
        | DOT
        | STAR
        | QUESTION
        | string
        | MINUS
        | MINUS_MINUS
		;

ifStatement
    : IF compare (SEMI|NL) THEN statement_block
        (ELIF compare (SEMI|NL) THEN statement_block)*
        (ELSE statement_block)?
      FI
    ;

statement_block:     statement+
		;


whileStatement
    :  'while' compare NL? DO statement+ DONE
    |'while' compare NL? DO statement+ DONE
    ;

until_statement
    : 'until' compare NL? DO statement+ DONE
    | 'until' compare NL? DO statement+ NL? DONE
    ;

doStatement
    :   DO statement+ DONE
    ;

forStatement
    : FOR ID IN list SEMI? NL? DO statement+ DONE
    | FOR for_loop_control NL? DO statement+ DONE
    ;

selectStatement
    : SELECT ID (IN list)? SEMI? NL? DO statement+ DONE
     ;

for_loop_control: LPAREN_LPAREN assignStatement SEMI for_compare SEMI expression RPAREN_RPAREN;

for_compare: compare;

variable:
        idOnly=ID ( associative_index | array_index)?
        |VARIABLE (associative_index | array_index)?

    ;

array_index:
		(LSQUARE index=expression RSQUARE)
		;




// <<- is converted by preprocessor to <<
hereDocument: HERE_START  ID;

functionDefinition
    :   ID LPAREN RPAREN compoundCommand
    |   FUNCTION ID LPAREN RPAREN compoundCommand
    |   FUNCTION ID compoundCommand
    ;


string : DQ_STRING | SQ_STRING | ESC;

arrayInitializer
    : LPAREN argument* RPAREN
    ;

list
    : argument+
    | LSQUARE argument* RSQUARE
    ;

statement_group: redirect1=redirect? statement_group1 redirect2=redirect? 
    	;
		
statement_group1
        :  LCURLY statement+ SEMI? RCURLY
        |  LPAREN statement+ SEMI? RPAREN
        ;

compoundCommand
        :   LCURLY statement+ RCURLY
        |   LPAREN statement+ RPAREN
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
    : DECLARE_A id1=ID (NL? EQ NL? associativeArrayInitializer)? NL? CMD_TERMINATOR?
    ;

associativeArrayInitializer
    : NL? LPAREN NL? (associativeArrayElement NL?) * RPAREN
    ;

associativeArrayElement
    :NL? LSQUARE key=argument RSQUARE EQ value=argument NL?
    ;

associativeArrayValue
    : string
    | NUMBER
    | boolean
    | variable
    | mathExpression
    | parameter
    ;

