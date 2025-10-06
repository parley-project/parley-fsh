<?php

$spellercss = '/speller/spellerStyle.css';
$word_win_src = '/speller/wordWindow.js';
$encoded_text = $_POST['checktext'];
$decoded_text = urldecode( $encoded_text );
$aspell_prog = 'c:\aspell\bin\aspell';
$lang = 'en_US';
$aspell_opts = "-a --lang=$lang";
$tempfiledir = "./";

# set the JavaScript 'wordtext' variable to the submitted text.
function print_text_var() {
	global $encoded_text;
	echo "var wordtext = unescape('$encoded_text');\n";
}


# set an element of the JavaScript 'words' array to a misspelled word
function print_words_elem( $word, $index ) {
	echo "words[$index] = '" . escape_quote( $word ) . "';\n";
}


# set an element of the JavaScript 'suggs' array to a list of suggestions
function print_suggs_elem( $suggs, $index ) {
	echo "suggs[$index] = [";
	foreach( $suggs as $key=>$val ) {
		if( $val ) {
			echo "'" . escape_quote( $val ) . "'";
			if ( $key+1 < count( $suggs )) {
				echo ", ";
			}
		}
	}
	echo "];\n";
}

# escape single quote
function escape_quote( $str ) {
	return preg_replace ( "/'/", "\\'", $str );
}


# handle a server-side error.
function error_handler( $err ) {
	echo "error = '" . escape_quote( $err ) . "';\n";
}

## get the list of misspelled words. Put the results in the javascript words array
## for each misspelled word, get suggestions and put in the javascript suggs array
function print_checker_results() {

	global $decoded_text;
	global $aspell_prog;
	global $aspell_opts;
	global $tempfiledir;

	# create temp file
	$tempfile = tempnam( $tempfiledir, 'aspell_data_' );

	# open temp file, add the submitted text.
    if( $fh = fopen( $tempfile, 'w' )) {
		# fwrite ( $fh, "!\n" );
		$lines = explode( "\n", $decoded_text );
		foreach( $lines as $key=>$value ) {
			# use carat on each line to escape possible aspell commands
			fwrite( $fh, "^$value\n" );
		}
		fclose( $fh );

		# exec aspell command
		$cmd = "$aspell_prog $aspell_opts < $tempfile";
		if( $aspellret = shell_exec( $cmd )) {
			echo /*\n $aspellret \n*/;
			$linesout = explode( "\n", $aspellret );
			$index = 0;
			# parse each line of aspell return
			foreach( $linesout as $key=>$val ) {
				$chardesc = substr( $val, 0, 1 );
				# if '&', then not in dictionary but has suggestions
				# if '#', then not in dictionary and no suggestions
				if( $chardesc == '&' || $chardesc == '#' ) {
					$line = explode( " ", $val, 5 );
					print_words_elem( $line[1], $index );
					$suggs = explode( ", ", $line[4] );
					print_suggs_elem( $suggs, $index );
					$index++;
				}
			}
		} else {
			error_handler( "System error: Aspell program execution failed (`$cmd`)" );
		}

	} else {
		error_handler( "System error: Could not open file '$tempfile' for writing" );
	}

	# close temp file, delete file
	unlink( $tempfile );
}



?>

<html>
<head>
<link rel="stylesheet" type="text/css" href="<?php echo $spellercss ?>" />
<script language="javascript" src="<?php echo $word_win_src ?>"></script>
<script language="javascript">
var suggs = new Array();
var words = new Array();
var error;
<?php

print_text_var();

print_checker_results();

?>

var wordWindowObj = new wordWindow();
wordWindowObj.originalSpellings = words;
wordWindowObj.suggestions = suggs;
wordWindowObj.text = wordtext;


function init_spell() {
	// check if any error occured during server-side processing
	if( error ) {
		alert( error );
	} else {
		// call the init_spell() function in the parent frameset
		if (parent.frames.length) {
			parent.init_spell( wordWindowObj );
		} else {
			alert('This page was loaded outside of a frameset. It might not display properly');
		}
	}
}



</script>

</head>
<body onLoad="init_spell();">

<script>
wordWindowObj.writeBody();
</script>

</body>
</html>

