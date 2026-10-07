// Generated from ExprParser.g4 by ANTLR 4.13.2
package us.bringardner.filesource.sh;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class ExprParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		PARAMETER_START=1, HERE_START=2, HERE_STRING=3, HERE_START_RM_TABS=4, 
		SEMI=5, SEMI_SEMI=6, SEMI_AMP=7, SEMI_SEMI_AMP=8, DOLLAR_PAREM=9, HASH=10, 
		NL=11, LT=12, LT_EQ=13, GT=14, GT_EQ=15, NOT=16, AND=17, OR=18, ESC_AND=19, 
		ESC_OR=20, IO_NUMBER=21, NUMBER=22, VARIABLE=23, INTEGER=24, DECIMAL=25, 
		DQ_STRING=26, SQ_STRING=27, LINE_CONTINUATION=28, ESC=29, WS=30, TRUE=31, 
		FALSE=32, COMMENT=33, LINE_COMMENT=34, SHEBANG=35, LOCAL=36, LCURLY=37, 
		RCURLY=38, FUNCTION=39, CRETURN=40, SPACE=41, TAB=42, QUOTE=43, BACKQUOTE=44, 
		CONTINUE=45, BREAK=46, FOR=47, SELECT=48, IN=49, WHILE=50, DONE=51, UNTIL=52, 
		IF=53, FI=54, THEN=55, ELSE=56, ELIF=57, SLASH=58, BACKSLASH=59, CASE=60, 
		ESAC=61, DOLLAR=62, PLUS_PLUS=63, MINUS_MINUS=64, PLUS_EQ=65, DOT=66, 
		DOT_DOT=67, PERC=68, PLUS=69, STAR=70, POW=71, DO=72, EQ=73, EQUALITY=74, 
		RX_EQUALITY=75, NOT_EQ=76, TEST_OP=77, MINUS=78, PIPE=79, AMP=80, TILDE=81, 
		QUESTION=82, TIME=83, LPAREN=84, RPAREN=85, LSQUARE=86, RSQUARE=87, REDIRECT_APPEND_OUT_2=88, 
		REDIRECT_APPEND_OUT=89, REDIRECT_READ_WRITE=90, REDIRECT_BOTH=91, REDIRECT_BOTH_2=92, 
		REDIRECT_INPUT_FROM_FID=93, COMMA=94, MINUS_ASSIGN=95, STAR_ASSIGN=96, 
		DIV_ASSIGN=97, MOD_ASSIGN=98, DIGIT=99, SPECIAL_UNIX=100, SPECIAL_WINDOWS=101, 
		POS=102, PERC_PERC=103, PERC_MINUS=104, PERC_PLUS=105, PERC_QUESTION=106, 
		ARG_ID=107, ID=108, LETTER_OR_DIGIT=109, COLON=110, AT=111, TEXT=112, 
		ARITH_EXPANSION=113, ARITH_COMMAND=114, DOLLAR_LPAREN_LPAREN=115, RPAREN_RPAREN=116, 
		LPAREN_LPAREN=117, NOT_CURLY=118, DECLARE_A=119, DIVIDE=120, RX_CHAR=121, 
		POSIX_CHAR_CLASS=122, CHAR_CLASS=123, PARAMETER_BODY=124, PARAMETER_END=125;
	public static final int
		RULE_expr = 0, RULE_array_element = 1, RULE_constant = 2, RULE_function_call = 3, 
		RULE_arguments = 4;
	private static String[] makeRuleNames() {
		return new String[] {
			"expr", "array_element", "constant", "function_call", "arguments"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'${'", "'<<'", "'<<<'", "'<<-'", "';'", "';;'", "';&'", "';;&'", 
			"'$('", "'#'", "'\\n'", "'<'", "'<='", "'>'", "'>='", "'!'", "'&&'", 
			"'||'", "'\\&&'", "'\\||'", null, null, null, null, null, null, null, 
			null, null, null, "'true'", "'false'", null, null, null, "'local'", "'{'", 
			"'}'", "'function'", "'\\r'", "' '", "'\\t'", "'''", "'`'", "'continue'", 
			"'break'", "'for'", "'select'", "'in'", "'while'", "'done'", "'until'", 
			"'if'", "'fi'", "'then'", "'else'", "'elif'", "'/'", "'\\'", "'case'", 
			"'esac'", "'$'", "'++'", "'--'", "'+='", "'.'", "'..'", "'%'", "'+'", 
			"'*'", "'**'", "'do'", "'='", null, "'=~'", null, null, "'-'", "'|'", 
			"'&'", "'~'", "'?'", "'time'", "'('", "')'", "'['", "']'", "'&>>'", "'>>'", 
			"'<>'", "'>&'", "'&>'", "'<&'", "','", "'-='", "'*='", "':^:='", "'%='", 
			null, null, null, "'^'", "'%%'", "'%-'", "'%+'", "'%?'", null, null, 
			null, "':'", "'@'", null, null, null, "'$(('", "'))'", "'(('", null, 
			null, "':^:'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "PARAMETER_START", "HERE_START", "HERE_STRING", "HERE_START_RM_TABS", 
			"SEMI", "SEMI_SEMI", "SEMI_AMP", "SEMI_SEMI_AMP", "DOLLAR_PAREM", "HASH", 
			"NL", "LT", "LT_EQ", "GT", "GT_EQ", "NOT", "AND", "OR", "ESC_AND", "ESC_OR", 
			"IO_NUMBER", "NUMBER", "VARIABLE", "INTEGER", "DECIMAL", "DQ_STRING", 
			"SQ_STRING", "LINE_CONTINUATION", "ESC", "WS", "TRUE", "FALSE", "COMMENT", 
			"LINE_COMMENT", "SHEBANG", "LOCAL", "LCURLY", "RCURLY", "FUNCTION", "CRETURN", 
			"SPACE", "TAB", "QUOTE", "BACKQUOTE", "CONTINUE", "BREAK", "FOR", "SELECT", 
			"IN", "WHILE", "DONE", "UNTIL", "IF", "FI", "THEN", "ELSE", "ELIF", "SLASH", 
			"BACKSLASH", "CASE", "ESAC", "DOLLAR", "PLUS_PLUS", "MINUS_MINUS", "PLUS_EQ", 
			"DOT", "DOT_DOT", "PERC", "PLUS", "STAR", "POW", "DO", "EQ", "EQUALITY", 
			"RX_EQUALITY", "NOT_EQ", "TEST_OP", "MINUS", "PIPE", "AMP", "TILDE", 
			"QUESTION", "TIME", "LPAREN", "RPAREN", "LSQUARE", "RSQUARE", "REDIRECT_APPEND_OUT_2", 
			"REDIRECT_APPEND_OUT", "REDIRECT_READ_WRITE", "REDIRECT_BOTH", "REDIRECT_BOTH_2", 
			"REDIRECT_INPUT_FROM_FID", "COMMA", "MINUS_ASSIGN", "STAR_ASSIGN", "DIV_ASSIGN", 
			"MOD_ASSIGN", "DIGIT", "SPECIAL_UNIX", "SPECIAL_WINDOWS", "POS", "PERC_PERC", 
			"PERC_MINUS", "PERC_PLUS", "PERC_QUESTION", "ARG_ID", "ID", "LETTER_OR_DIGIT", 
			"COLON", "AT", "TEXT", "ARITH_EXPANSION", "ARITH_COMMAND", "DOLLAR_LPAREN_LPAREN", 
			"RPAREN_RPAREN", "LPAREN_LPAREN", "NOT_CURLY", "DECLARE_A", "DIVIDE", 
			"RX_CHAR", "POSIX_CHAR_CLASS", "CHAR_CLASS", "PARAMETER_BODY", "PARAMETER_END"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "ExprParser.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ExprParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprContext extends ParserRuleContext {
		public ExprContext elment_array;
		public ExprContext left;
		public ExprContext ternaryTest;
		public Token op;
		public ExprContext unaryMinus;
		public ExprContext unaryPlus;
		public ExprContext unaryNot;
		public ExprContext single;
		public ExprContext right;
		public ExprContext ternaryTrue;
		public ExprContext ternaryFalse;
		public ExprContext elment_index;
		public ExprContext elment_indexes;
		public Function_callContext function_call() {
			return getRuleContext(Function_callContext.class,0);
		}
		public Array_elementContext array_element() {
			return getRuleContext(Array_elementContext.class,0);
		}
		public TerminalNode MINUS() { return getToken(ExprParser.MINUS, 0); }
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode PLUS() { return getToken(ExprParser.PLUS, 0); }
		public TerminalNode NOT() { return getToken(ExprParser.NOT, 0); }
		public TerminalNode LPAREN() { return getToken(ExprParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(ExprParser.RPAREN, 0); }
		public TerminalNode ID() { return getToken(ExprParser.ID, 0); }
		public ConstantContext constant() {
			return getRuleContext(ConstantContext.class,0);
		}
		public TerminalNode STAR() { return getToken(ExprParser.STAR, 0); }
		public TerminalNode SLASH() { return getToken(ExprParser.SLASH, 0); }
		public TerminalNode LT() { return getToken(ExprParser.LT, 0); }
		public TerminalNode GT() { return getToken(ExprParser.GT, 0); }
		public TerminalNode PERC() { return getToken(ExprParser.PERC, 0); }
		public TerminalNode POW() { return getToken(ExprParser.POW, 0); }
		public TerminalNode GT_EQ() { return getToken(ExprParser.GT_EQ, 0); }
		public TerminalNode LT_EQ() { return getToken(ExprParser.LT_EQ, 0); }
		public TerminalNode NOT_EQ() { return getToken(ExprParser.NOT_EQ, 0); }
		public TerminalNode EQUALITY() { return getToken(ExprParser.EQUALITY, 0); }
		public TerminalNode OR() { return getToken(ExprParser.OR, 0); }
		public TerminalNode AND() { return getToken(ExprParser.AND, 0); }
		public TerminalNode QUESTION() { return getToken(ExprParser.QUESTION, 0); }
		public List<TerminalNode> LSQUARE() { return getTokens(ExprParser.LSQUARE); }
		public TerminalNode LSQUARE(int i) {
			return getToken(ExprParser.LSQUARE, i);
		}
		public List<TerminalNode> RSQUARE() { return getTokens(ExprParser.RSQUARE); }
		public TerminalNode RSQUARE(int i) {
			return getToken(ExprParser.RSQUARE, i);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).enterExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).exitExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprParserVisitor ) return ((ExprParserVisitor<? extends T>)visitor).visitExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		return expr(0);
	}

	private ExprContext expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExprContext _localctx = new ExprContext(_ctx, _parentState);
		ExprContext _prevctx = _localctx;
		int _startState = 0;
		enterRecursionRule(_localctx, 0, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(25);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
			case 1:
				{
				setState(11);
				function_call();
				}
				break;
			case 2:
				{
				setState(12);
				array_element();
				}
				break;
			case 3:
				{
				setState(13);
				((ExprContext)_localctx).op = match(MINUS);
				setState(14);
				((ExprContext)_localctx).unaryMinus = expr(14);
				}
				break;
			case 4:
				{
				setState(15);
				((ExprContext)_localctx).op = match(PLUS);
				setState(16);
				((ExprContext)_localctx).unaryPlus = expr(13);
				}
				break;
			case 5:
				{
				setState(17);
				((ExprContext)_localctx).op = match(NOT);
				setState(18);
				((ExprContext)_localctx).unaryNot = expr(12);
				}
				break;
			case 6:
				{
				setState(19);
				match(LPAREN);
				setState(20);
				((ExprContext)_localctx).single = expr(0);
				setState(21);
				match(RPAREN);
				}
				break;
			case 7:
				{
				setState(23);
				match(ID);
				}
				break;
			case 8:
				{
				setState(24);
				constant();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(69);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(67);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
					case 1:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(27);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(28);
						((ExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==SLASH || _la==STAR) ) {
							((ExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(29);
						((ExprContext)_localctx).right = expr(12);
						}
						break;
					case 2:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(30);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(31);
						((ExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PLUS || _la==MINUS) ) {
							((ExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(32);
						((ExprContext)_localctx).right = expr(11);
						}
						break;
					case 3:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(33);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(34);
						((ExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==LT || _la==GT) ) {
							((ExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(35);
						((ExprContext)_localctx).right = expr(10);
						}
						break;
					case 4:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(36);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(37);
						((ExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PERC || _la==POW) ) {
							((ExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(38);
						((ExprContext)_localctx).right = expr(9);
						}
						break;
					case 5:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(39);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(40);
						((ExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(((((_la - 13)) & ~0x3f) == 0 && ((1L << (_la - 13)) & -9223372036854775803L) != 0)) ) {
							((ExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(41);
						((ExprContext)_localctx).right = expr(8);
						}
						break;
					case 6:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(42);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(43);
						((ExprContext)_localctx).op = match(EQUALITY);
						setState(44);
						((ExprContext)_localctx).right = expr(7);
						}
						break;
					case 7:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(45);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(46);
						((ExprContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==AND || _la==OR) ) {
							((ExprContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(47);
						((ExprContext)_localctx).right = expr(6);
						}
						break;
					case 8:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.ternaryTest = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(48);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(49);
						match(QUESTION);
						setState(50);
						((ExprContext)_localctx).ternaryTrue = expr(0);
						setState(51);
						match(AND);
						setState(52);
						((ExprContext)_localctx).ternaryFalse = expr(5);
						}
						break;
					case 9:
						{
						_localctx = new ExprContext(_parentctx, _parentState);
						_localctx.elment_array = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(54);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(55);
						match(LSQUARE);
						setState(56);
						((ExprContext)_localctx).elment_index = expr(0);
						setState(57);
						match(RSQUARE);
						setState(64);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(58);
								match(LSQUARE);
								setState(59);
								((ExprContext)_localctx).elment_indexes = expr(0);
								setState(60);
								match(RSQUARE);
								}
								} 
							}
							setState(66);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,1,_ctx);
						}
						}
						break;
					}
					} 
				}
				setState(71);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Array_elementContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(ExprParser.ID, 0); }
		public List<TerminalNode> LSQUARE() { return getTokens(ExprParser.LSQUARE); }
		public TerminalNode LSQUARE(int i) {
			return getToken(ExprParser.LSQUARE, i);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> RSQUARE() { return getTokens(ExprParser.RSQUARE); }
		public TerminalNode RSQUARE(int i) {
			return getToken(ExprParser.RSQUARE, i);
		}
		public Array_elementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_array_element; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).enterArray_element(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).exitArray_element(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprParserVisitor ) return ((ExprParserVisitor<? extends T>)visitor).visitArray_element(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Array_elementContext array_element() throws RecognitionException {
		Array_elementContext _localctx = new Array_elementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_array_element);
		try {
			int _alt;
			setState(90);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(72);
				match(ID);
				setState(73);
				match(LSQUARE);
				setState(74);
				expr(0);
				setState(75);
				match(RSQUARE);
				setState(82);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(76);
						match(LSQUARE);
						setState(77);
						expr(0);
						setState(78);
						match(RSQUARE);
						}
						} 
					}
					setState(84);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,4,_ctx);
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(85);
				match(ID);
				setState(86);
				match(LSQUARE);
				setState(87);
				expr(0);
				setState(88);
				match(RSQUARE);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConstantContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(ExprParser.NUMBER, 0); }
		public ConstantContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constant; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).enterConstant(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).exitConstant(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprParserVisitor ) return ((ExprParserVisitor<? extends T>)visitor).visitConstant(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstantContext constant() throws RecognitionException {
		ConstantContext _localctx = new ConstantContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_constant);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(92);
			match(NUMBER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Function_callContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(ExprParser.ID, 0); }
		public TerminalNode LPAREN() { return getToken(ExprParser.LPAREN, 0); }
		public ArgumentsContext arguments() {
			return getRuleContext(ArgumentsContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(ExprParser.RPAREN, 0); }
		public Function_callContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_function_call; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).enterFunction_call(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).exitFunction_call(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprParserVisitor ) return ((ExprParserVisitor<? extends T>)visitor).visitFunction_call(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Function_callContext function_call() throws RecognitionException {
		Function_callContext _localctx = new Function_callContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_function_call);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(94);
			match(ID);
			setState(95);
			match(LPAREN);
			setState(96);
			arguments();
			setState(97);
			match(RPAREN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArgumentsContext extends ParserRuleContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public TerminalNode COMMA() { return getToken(ExprParser.COMMA, 0); }
		public ArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arguments; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).enterArguments(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ExprParserListener ) ((ExprParserListener)listener).exitArguments(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ExprParserVisitor ) return ((ExprParserVisitor<? extends T>)visitor).visitArguments(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentsContext arguments() throws RecognitionException {
		ArgumentsContext _localctx = new ArgumentsContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_arguments);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(99);
			expr(0);
			{
			setState(100);
			match(COMMA);
			setState(101);
			expr(0);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 0:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 11);
		case 1:
			return precpred(_ctx, 10);
		case 2:
			return precpred(_ctx, 9);
		case 3:
			return precpred(_ctx, 8);
		case 4:
			return precpred(_ctx, 7);
		case 5:
			return precpred(_ctx, 6);
		case 6:
			return precpred(_ctx, 5);
		case 7:
			return precpred(_ctx, 4);
		case 8:
			return precpred(_ctx, 15);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001}h\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002\u0002"+
		"\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0003\u0000\u001a\b\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0005\u0000?\b\u0000\n\u0000\f\u0000"+
		"B\t\u0000\u0005\u0000D\b\u0000\n\u0000\f\u0000G\t\u0000\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0005\u0001Q\b\u0001\n\u0001\f\u0001T\t\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001[\b\u0001\u0001"+
		"\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0000"+
		"\u0001\u0000\u0005\u0000\u0002\u0004\u0006\b\u0000\u0006\u0002\u0000:"+
		":FF\u0002\u0000EENN\u0002\u0000\f\f\u000e\u000e\u0002\u0000DDGG\u0003"+
		"\u0000\r\r\u000f\u000fLL\u0001\u0000\u0011\u0012u\u0000\u0019\u0001\u0000"+
		"\u0000\u0000\u0002Z\u0001\u0000\u0000\u0000\u0004\\\u0001\u0000\u0000"+
		"\u0000\u0006^\u0001\u0000\u0000\u0000\bc\u0001\u0000\u0000\u0000\n\u000b"+
		"\u0006\u0000\uffff\uffff\u0000\u000b\u001a\u0003\u0006\u0003\u0000\f\u001a"+
		"\u0003\u0002\u0001\u0000\r\u000e\u0005N\u0000\u0000\u000e\u001a\u0003"+
		"\u0000\u0000\u000e\u000f\u0010\u0005E\u0000\u0000\u0010\u001a\u0003\u0000"+
		"\u0000\r\u0011\u0012\u0005\u0010\u0000\u0000\u0012\u001a\u0003\u0000\u0000"+
		"\f\u0013\u0014\u0005T\u0000\u0000\u0014\u0015\u0003\u0000\u0000\u0000"+
		"\u0015\u0016\u0005U\u0000\u0000\u0016\u001a\u0001\u0000\u0000\u0000\u0017"+
		"\u001a\u0005l\u0000\u0000\u0018\u001a\u0003\u0004\u0002\u0000\u0019\n"+
		"\u0001\u0000\u0000\u0000\u0019\f\u0001\u0000\u0000\u0000\u0019\r\u0001"+
		"\u0000\u0000\u0000\u0019\u000f\u0001\u0000\u0000\u0000\u0019\u0011\u0001"+
		"\u0000\u0000\u0000\u0019\u0013\u0001\u0000\u0000\u0000\u0019\u0017\u0001"+
		"\u0000\u0000\u0000\u0019\u0018\u0001\u0000\u0000\u0000\u001aE\u0001\u0000"+
		"\u0000\u0000\u001b\u001c\n\u000b\u0000\u0000\u001c\u001d\u0007\u0000\u0000"+
		"\u0000\u001dD\u0003\u0000\u0000\f\u001e\u001f\n\n\u0000\u0000\u001f \u0007"+
		"\u0001\u0000\u0000 D\u0003\u0000\u0000\u000b!\"\n\t\u0000\u0000\"#\u0007"+
		"\u0002\u0000\u0000#D\u0003\u0000\u0000\n$%\n\b\u0000\u0000%&\u0007\u0003"+
		"\u0000\u0000&D\u0003\u0000\u0000\t\'(\n\u0007\u0000\u0000()\u0007\u0004"+
		"\u0000\u0000)D\u0003\u0000\u0000\b*+\n\u0006\u0000\u0000+,\u0005J\u0000"+
		"\u0000,D\u0003\u0000\u0000\u0007-.\n\u0005\u0000\u0000./\u0007\u0005\u0000"+
		"\u0000/D\u0003\u0000\u0000\u000601\n\u0004\u0000\u000012\u0005R\u0000"+
		"\u000023\u0003\u0000\u0000\u000034\u0005\u0011\u0000\u000045\u0003\u0000"+
		"\u0000\u00055D\u0001\u0000\u0000\u000067\n\u000f\u0000\u000078\u0005V"+
		"\u0000\u000089\u0003\u0000\u0000\u00009@\u0005W\u0000\u0000:;\u0005V\u0000"+
		"\u0000;<\u0003\u0000\u0000\u0000<=\u0005W\u0000\u0000=?\u0001\u0000\u0000"+
		"\u0000>:\u0001\u0000\u0000\u0000?B\u0001\u0000\u0000\u0000@>\u0001\u0000"+
		"\u0000\u0000@A\u0001\u0000\u0000\u0000AD\u0001\u0000\u0000\u0000B@\u0001"+
		"\u0000\u0000\u0000C\u001b\u0001\u0000\u0000\u0000C\u001e\u0001\u0000\u0000"+
		"\u0000C!\u0001\u0000\u0000\u0000C$\u0001\u0000\u0000\u0000C\'\u0001\u0000"+
		"\u0000\u0000C*\u0001\u0000\u0000\u0000C-\u0001\u0000\u0000\u0000C0\u0001"+
		"\u0000\u0000\u0000C6\u0001\u0000\u0000\u0000DG\u0001\u0000\u0000\u0000"+
		"EC\u0001\u0000\u0000\u0000EF\u0001\u0000\u0000\u0000F\u0001\u0001\u0000"+
		"\u0000\u0000GE\u0001\u0000\u0000\u0000HI\u0005l\u0000\u0000IJ\u0005V\u0000"+
		"\u0000JK\u0003\u0000\u0000\u0000KR\u0005W\u0000\u0000LM\u0005V\u0000\u0000"+
		"MN\u0003\u0000\u0000\u0000NO\u0005W\u0000\u0000OQ\u0001\u0000\u0000\u0000"+
		"PL\u0001\u0000\u0000\u0000QT\u0001\u0000\u0000\u0000RP\u0001\u0000\u0000"+
		"\u0000RS\u0001\u0000\u0000\u0000S[\u0001\u0000\u0000\u0000TR\u0001\u0000"+
		"\u0000\u0000UV\u0005l\u0000\u0000VW\u0005V\u0000\u0000WX\u0003\u0000\u0000"+
		"\u0000XY\u0005W\u0000\u0000Y[\u0001\u0000\u0000\u0000ZH\u0001\u0000\u0000"+
		"\u0000ZU\u0001\u0000\u0000\u0000[\u0003\u0001\u0000\u0000\u0000\\]\u0005"+
		"\u0016\u0000\u0000]\u0005\u0001\u0000\u0000\u0000^_\u0005l\u0000\u0000"+
		"_`\u0005T\u0000\u0000`a\u0003\b\u0004\u0000ab\u0005U\u0000\u0000b\u0007"+
		"\u0001\u0000\u0000\u0000cd\u0003\u0000\u0000\u0000de\u0005^\u0000\u0000"+
		"ef\u0003\u0000\u0000\u0000f\t\u0001\u0000\u0000\u0000\u0006\u0019@CER"+
		"Z";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}