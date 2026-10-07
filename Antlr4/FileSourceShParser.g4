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
	  // & ends a statement too: cmd & next
	  white* statement1 (WS* bg=AMP WS* | WS* (NL|SEMI|EOF))
	| conditionalStatement  (NL|SEMI|EOF)
	;
	
statement1:
      // ! before any command or pipeline inverts its status
      NOT WS+ negated=statement1
    | DBL_TEST
    | ifStatement
    | mathStatement
    | whileStatement
    | forStatement
    | selectStatement
    | caseStatement
    | assignStatement
    | functionDefinition
    | until_statement
    | doStatement
    // before pipeStatement, which would take local as a command name
    | declareAssociativeArrayStatement
    // after assignments and function definitions, which also start with a name (where commandStatement was)
    | pipeStatement
    | loop_controll_statement
    | boolean_statement
    | compareStatement
    | job_control_statement
    
    ;

loop_controll_statement: 
			  BREAK WS* NUMBER?
            | CONTINUE WS* NUMBER?
            ;

// a=1 b=2: one or more assignments with no command
// x=$(cmd) 2>/dev/null: an assignment may take redirects
assignStatement: assignment (WS+ assignment)* (WS* redirect)? WS*
		;
		
// the value is a word, like a command argument: x=sub-dir and y=$x$x are text, x=$((1+2)) is a number
assignment:		
      // a=(x y), a+=(z)
      (LOCAL WS)? WS* id1=ID WS* op=(EQ|PLUS_EQ) WS* arrayInitializer
      // x=1, x+=1, a[2]=c, m[key]=v
    | (LOCAL WS)? WS* id1=ID (WS* (associative_index | array_index))? WS* op=(EQ|PLUS_EQ) WS* value=argument?
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
        | SPECIAL_UNIX
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
             | DO | TIME | FUNCTION | CONTINUE | BREAK | LSQUARE | RSQUARE | NOT
             // arithmetic operators, for let x++ and let x+=2
             | PLUS_PLUS | PLUS_EQ | MINUS_ASSIGN | STAR_ASSIGN | MOD_ASSIGN | POW | EQUALITY | NOT_EQ
             | TEST_OP
             // # in a word is text (a#b); at the start of a word it begins a comment
             | HASH
             // a lone - _ + = or ~ ( _* )
             | SPECIAL_UNIX
             // !(*.o), @(a|b) ...
             | EXTGLOB
             // [[:alpha:]]*: a character class in a pattern
             | POSIX_CHAR_CLASS)
    | string
    | argVariable
    | parameter
    | mathExpression
    | arg_command_substitution
    | braceExpansion
    | procSubst=PROC_SUBST
    | procSubstOut=PROC_SUBST_OUT
    ;

// $name, $1, $? ... (a bare name is a literal part of the word); $name[index] indexes an array
argVariable: VARIABLE (associative_index | array_index)? ;
    
signed_number: (MINUS|PLUS|PERC)? NUMBER;    


// one alternative: with two that differ only at the end, the parser had to read the whole
// command before it could choose (seconds for a long path)
commandStatement:
      // VAR=value cmd: the assignments are for this command only
      WS*	redirect1=redirect? WS* (prefix+=assignment WS+)* command (WS+ argument)* WS* (hereDocument WS*)? redirect2=redirect?
    ;
    
    
redirect: (redirect_one WS*)+;
 
 // [n]op word: 2>file, >>log, <in, 2>&1, >&2, 3<&-, &>out; or a here-document (done <<EOF)
 redirect_one:
      fd=(IO_NUMBER|VARFD)? redirectionOperator WS* target=argument
    | fd=(IO_NUMBER|VARFD)? HERE_START WS* hereId=ID
    ;



// a word with quotes or expansions is expanded when the command runs, and its first field is the
// command name ($cmd args, $(echo ls) -l). Plain text is never one, so done, fi ... stay keywords.
command: cmdWord=commandWord
		| path
		| ID
		| TRUE   // builtins, so ! false and false | true work
		| FALSE
		;

commandWord: commandWordStart argumentPart* ;

commandWordStart: string | argVariable | parameter | arg_command_substitution ;


// also a single command or group: an alternative that repeated the command before the first |
// made the fast SLL parse fail on every command
pipeStatement:
     white* (TIME white*)? parg=ARG_ID? white* (NOT white*)? pipeableStatement (pipeOp pipeableStatement)* 
    ;
    
pipeableStatement:
		commandStatement
		| statement_group WS*  // a command takes the spaces before | itself; a group did not, so "{ ...; } | x" failed
		// loops and other compound commands: ... | while read x; do ...; done
		// ... and they take redirects: while read l; do ...; done < file
		| whileStatement (WS* redirect)? WS*
		| until_statement (WS* redirect)? WS*
		| forStatement (WS* redirect)? WS*
		| ifStatement (WS* redirect)? WS*
		| caseStatement (WS* redirect)? WS*
		| selectStatement (WS* redirect)? WS*
		// declare -f f | head, declare -p x | cat
		| declareAssociativeArrayStatement
		;
		    
pipeOp:
	PIPE white* AMP?
	;    

// [ words ] (test's arguments, then a redirect may follow); the second form is this shell's grouping
compareStatement:
      LSQUARE testWords RSQUARE (WS* redirect)? WS* statement?
    | LSQUARE WS* simpleCompare=compare WS* RSQUARE WS* statement?
    ;

testWords: (WS+ argument)* WS+ ;

// (( x > 3 )): status 0 if the value is not 0
mathStatement:
      ARITH_COMMAND
    | mathExpression
    | parameter
    ;

// $(( x * 2 )): the text between the parentheses is evaluated by Arithmetic
mathExpression: ARITH_EXPANSION | DOLLAR_BRACKET ;

boolean_statement: boolean;

compare : 
		  // if ( cmds ); then, while { cmds; }: the group's status
		  WS* group=statement_group1 (';' WS*)?
		  // if declare -F f >/dev/null; then
		| WS* declare=declareAssociativeArrayStatement (';' WS*)?
		| WS* LSQUARE testWords RSQUARE (';' WS*)?   // if [ -e f -a -d d ]
		| WS* ARITH_COMMAND (';' WS*)?   // if (( x > 3 )); while (( i < 10 ))
		| WS* DBL_TEST (';' WS*)?        // if [[ $x == a* ]]
		| WS* compare_prime (';' WS*)?
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
    // -lt -le -gt -ge (-eq and -ne are EQUALITY and NOT_EQ). They are read only here: elsewhere
    // they are ordinary words (ls -lt, echo -ne)
    | left=compare_prime WS* TEST_OP WS* right=compare_prime
    | left=compare_prime WS* RX_EQUALITY WS* regular_expression    
    // before expression: if f; then runs the command f (it was read as the variable f)
    | commandStatement
    | expression
    ;

// -f file, -d dir ... (the operator is an option, so [ and ] are not taken for one)
file_test: WS* op=ARG_ID WS+ target=argument WS*;

associative_index:
		(LSQUARE ID RSQUARE)
		| (LSQUARE index=string RSQUARE)
		// m[$section.$key]=v: any word up to ]
		| (LSQUARE assocKey RSQUARE)
		;

regular_expression:	rx_pattern+ ;


expression:
      simpleTerm=term
    | variable WS* postOp=(PLUS_PLUS|MINUS_MINUS)
    | preOp=(PLUS_PLUS|MINUS_MINUS) WS* variable
    | variable WS* op=PLUS_EQ WS* expression
    | variable WS* op=MINUS_ASSIGN WS* expression
    | variable WS* op=STAR_ASSIGN WS* expression
    | variable WS* op=DIV_ASSIGN WS* expression
    | variable WS* op=MOD_ASSIGN WS* expression
    | expression WS* op=(PLUS | MINUS| PERC) WS* complexTerm=term
       ;


term:
      factor
    | term WS* op=(STAR | DIVIDE | SLASH | PERC | POW) WS* factor   // / divides (DIVIDE is :^:, from the preprocessor)
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
    | LPAREN WS* expression WS* RPAREN
    | boolean
    | sign=(MINUS | PLUS) WS* factor   // -1, -x
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
    | HERE_STRING // <<<word
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
     white* UNTIL white* compare white* (';' white*)? doStatement
    ;

doStatement:
     white* DO white* statement* white* DONE
    ;

forStatement:
     white* FOR white* ID white* IN white* list white* SEMI? doStatement
    // for x; do ... done: the positional parameters
    | white* FOR white* ID white* SEMI? white* doStatement
    | white* FOR white* for_loop_control white* SEMI? doStatement
    ;

selectStatement:
     white* SELECT white* ID white* (IN white* path)? white* SEMI? white*  NL?white*  doStatement
    |white* SELECT white* ID white* (IN white* list)? white* SEMI? white*  NL?white*  doStatement
     ;

// for (( init; condition; step )): each part is arithmetic and may be empty
for_loop_control: ARITH_COMMAND ;

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
     white* (FUNCTION white*)? fname=funcName white* (LPAREN white* RPAREN white*)? compoundCommand
    
    ;

// my-func, lib.init, ns::f
funcName: ID ((MINUS | DOT | COLON | ID | NUMBER)* ID)? ;

string : DQ_STRING | SQ_STRING | ANSI_STRING | ESC;

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
		
// the redirects apply to the whole group: { ...; } 2>/dev/null, ( ... ) > out
statement_group1
 		: redirect1=redirect?  LCURLY white* statement_or_statement1* white* RCURLY (WS* redirect2=redirect)?
        | redirect1=redirect?  LPAREN white* statement_or_statement1* white* RPAREN (WS* redirect2=redirect)?
		;



compoundCommand:
          // f() { ...; } > file: the redirect applies each time f runs
          redirect1=redirect?  LCURLY white* statement* white* RCURLY (WS* redirect2=redirect)?
        // f() ( ... ): the body runs in a subshell, and its last command needs no ;
        | subshell=LPAREN white* statement_or_statement1* white* RPAREN (WS* redirect2=redirect)?
        
        ;

// parentheses nest: $(echo $(date)). The lexer reads )) as one token, so it may close two levels.
arg_command_substitution:
			DOLLAR_PAREM cmd_part* RPAREN
			| '`' ~'`'* '`'
			;

cmd_part:
			// $(case x in a) ...;; esac): the ) after a pattern does not end the $( )
			CASE case_part* ESAC
			| ~(DOLLAR_PAREM | LPAREN | RPAREN | DOLLAR_LPAREN_LPAREN | LPAREN_LPAREN)
			| (DOLLAR_PAREM | LPAREN) cmd_part* RPAREN
			| (DOLLAR_LPAREN_LPAREN | LPAREN_LPAREN) cmd_part* RPAREN RPAREN
			;

// inside case ... esac in $( ): parentheses need not balance
case_part:
			CASE case_part* ESAC
			| (DOLLAR_PAREM | LPAREN) cmd_part* RPAREN
			| (DOLLAR_LPAREN_LPAREN | LPAREN_LPAREN) cmd_part* RPAREN RPAREN
			| ~(ESAC | CASE | DOLLAR_PAREM | DOLLAR_LPAREN_LPAREN | LPAREN_LPAREN)
			;


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
// declare -opts name[=value] ...: -A and -a arrays, -i integer, -x export
declareAssociativeArrayStatement:
     white* DECLARE_A (WS+ declareItem)* (WS* redirect)? WS*
    // local -n r=$1, local -a arr=(1 2), local x y=2 (local x=1 is an assignStatement)
    | white* LOCAL (WS+ localOpts+=ARG_ID)* (WS+ declareItem)+ (WS* redirect)? WS*
    ;

declareItem: id1=ID (EQ (associativeArrayInitializer | arrayInitializer | value=argument)?)? 
    // declare -F "cmd_$c", declare "$name=$value": a name made by expansion
    | word=argument
    ;

associativeArrayInitializer:
     white* LPAREN white* (associativeArrayElement white*) * RPAREN    
    ;


// text before and after the braces is part of the word (see Argument)
// {a} and {} are text (no comma and no ..)
braceExpansion: LCURLY (braceRange|braceArgList|literal=braceItem?) RCURLY
	;
	
// {a,b{1,2},}: at least one comma ({a} is text); an item is text and braces, and may be empty
braceArgList: braceItem? (COMMA braceItem?)+;
braceItem: (associativeArrayValue | braceExpansion)+;
braceRange: start=braceBound DOT_DOT end=braceBound (DOT_DOT incr=braceBound)?;
// {-2..2}
braceBound: MINUS? associativeArrayValue;

associativeArrayElement:
    white* LSQUARE key+=argument RSQUARE WS* EQ WS* value=argument? white*
    // [b c]=5: a key may have spaces
    | white* LSQUARE keyText=assocKey RSQUARE WS* EQ WS* value=argument? white*
    ;

// the text of a key up to ]: a b, "x y", $k
assocKey: ~RSQUARE+;

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

