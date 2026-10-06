#!/bin/sh
# Regenerate the ANTLR parsers in ../fssh from the grammars in this directory.
# Run it from anywhere: sh Antlr4/build.sh
#
# FileSourceShPreProcessorBrace* in fssh has no grammar here, so it is not regenerated.

cd "$(dirname "$0")" || exit 1

java -cp ../lib/antlr-4.13.2-complete.jar org.antlr.v4.Tool -visitor -listener \
	-package us.bringardner.filesource.sh -o ../fssh/us/bringardner/filesource/sh \
	FileSourceShLexer.g4 FileSourceShParser.g4 ExprParser.g4 FileSourceShPreProcessor.g4
