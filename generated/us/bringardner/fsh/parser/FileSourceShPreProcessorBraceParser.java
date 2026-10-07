// Generated from /Volumes/Data/eclipse-workspace-jmail/BjlFileSystemConsole/Antlr4/FileSourceShPreProcessorBrace.g4 by ANTLR 4.13.2
package us.bringardner.fsh.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class FileSourceShPreProcessorBraceParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		DIGIT=1, LETTER_OR_DIGIT=2, LCURLY=3, RCURLY=4, DOT_DOT=5, TEXT=6, LETTER=7, 
		WS=8;
	public static final int
		RULE_expansion_list = 0, RULE_brace_expantion = 1, RULE_first = 2, RULE_range_value = 3, 
		RULE_number = 4, RULE_word = 5, RULE_text = 6;
	private static String[] makeRuleNames() {
		return new String[] {
			"expansion_list", "brace_expantion", "first", "range_value", "number", 
			"word", "text"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, "'{'", "'}'", "'..'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "DIGIT", "LETTER_OR_DIGIT", "LCURLY", "RCURLY", "DOT_DOT", "TEXT", 
			"LETTER", "WS"
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
	public String getGrammarFileName() { return "FileSourceShPreProcessorBrace.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public FileSourceShPreProcessorBraceParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class Expansion_listContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(FileSourceShPreProcessorBraceParser.EOF, 0); }
		public List<TextContext> text() {
			return getRuleContexts(TextContext.class);
		}
		public TextContext text(int i) {
			return getRuleContext(TextContext.class,i);
		}
		public List<Brace_expantionContext> brace_expantion() {
			return getRuleContexts(Brace_expantionContext.class);
		}
		public Brace_expantionContext brace_expantion(int i) {
			return getRuleContext(Brace_expantionContext.class,i);
		}
		public Expansion_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expansion_list; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterExpansion_list(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitExpansion_list(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitExpansion_list(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Expansion_listContext expansion_list() throws RecognitionException {
		Expansion_listContext _localctx = new Expansion_listContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_expansion_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(18);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 76L) != 0)) {
				{
				setState(16);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case TEXT:
					{
					setState(14);
					text();
					}
					break;
				case LETTER_OR_DIGIT:
				case LCURLY:
					{
					setState(15);
					brace_expantion();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(20);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(21);
			match(EOF);
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
	public static class Brace_expantionContext extends ParserRuleContext {
		public Range_valueContext start;
		public Range_valueContext end;
		public NumberContext increment;
		public WordContext postscript;
		public FirstContext first() {
			return getRuleContext(FirstContext.class,0);
		}
		public List<TerminalNode> DOT_DOT() { return getTokens(FileSourceShPreProcessorBraceParser.DOT_DOT); }
		public TerminalNode DOT_DOT(int i) {
			return getToken(FileSourceShPreProcessorBraceParser.DOT_DOT, i);
		}
		public TerminalNode RCURLY() { return getToken(FileSourceShPreProcessorBraceParser.RCURLY, 0); }
		public List<Range_valueContext> range_value() {
			return getRuleContexts(Range_valueContext.class);
		}
		public Range_valueContext range_value(int i) {
			return getRuleContext(Range_valueContext.class,i);
		}
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public WordContext word() {
			return getRuleContext(WordContext.class,0);
		}
		public Brace_expantionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_brace_expantion; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterBrace_expantion(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitBrace_expantion(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitBrace_expantion(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Brace_expantionContext brace_expantion() throws RecognitionException {
		Brace_expantionContext _localctx = new Brace_expantionContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_brace_expantion);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(23);
			first();
			setState(24);
			((Brace_expantionContext)_localctx).start = range_value();
			setState(25);
			match(DOT_DOT);
			setState(26);
			((Brace_expantionContext)_localctx).end = range_value();
			setState(29);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DOT_DOT) {
				{
				setState(27);
				match(DOT_DOT);
				setState(28);
				((Brace_expantionContext)_localctx).increment = number();
				}
			}

			setState(31);
			match(RCURLY);
			setState(33);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				{
				setState(32);
				((Brace_expantionContext)_localctx).postscript = word();
				}
				break;
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

	@SuppressWarnings("CheckReturnValue")
	public static class FirstContext extends ParserRuleContext {
		public WordContext preamble;
		public TerminalNode LCURLY() { return getToken(FileSourceShPreProcessorBraceParser.LCURLY, 0); }
		public WordContext word() {
			return getRuleContext(WordContext.class,0);
		}
		public FirstContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_first; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterFirst(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitFirst(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitFirst(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FirstContext first() throws RecognitionException {
		FirstContext _localctx = new FirstContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_first);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			{
			setState(36);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==LETTER_OR_DIGIT) {
				{
				setState(35);
				((FirstContext)_localctx).preamble = word();
				}
			}

			setState(38);
			match(LCURLY);
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

	@SuppressWarnings("CheckReturnValue")
	public static class Range_valueContext extends ParserRuleContext {
		public Token letter;
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public TerminalNode LETTER() { return getToken(FileSourceShPreProcessorBraceParser.LETTER, 0); }
		public Range_valueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_range_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterRange_value(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitRange_value(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitRange_value(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Range_valueContext range_value() throws RecognitionException {
		Range_valueContext _localctx = new Range_valueContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_range_value);
		try {
			setState(42);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DIGIT:
				enterOuterAlt(_localctx, 1);
				{
				setState(40);
				number();
				}
				break;
			case LETTER:
				enterOuterAlt(_localctx, 2);
				{
				setState(41);
				((Range_valueContext)_localctx).letter = match(LETTER);
				}
				break;
			default:
				throw new NoViableAltException(this);
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
	public static class NumberContext extends ParserRuleContext {
		public List<TerminalNode> DIGIT() { return getTokens(FileSourceShPreProcessorBraceParser.DIGIT); }
		public TerminalNode DIGIT(int i) {
			return getToken(FileSourceShPreProcessorBraceParser.DIGIT, i);
		}
		public NumberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_number; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterNumber(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitNumber(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitNumber(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NumberContext number() throws RecognitionException {
		NumberContext _localctx = new NumberContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_number);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(45); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(44);
				match(DIGIT);
				}
				}
				setState(47); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==DIGIT );
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
	public static class WordContext extends ParserRuleContext {
		public List<TerminalNode> LETTER_OR_DIGIT() { return getTokens(FileSourceShPreProcessorBraceParser.LETTER_OR_DIGIT); }
		public TerminalNode LETTER_OR_DIGIT(int i) {
			return getToken(FileSourceShPreProcessorBraceParser.LETTER_OR_DIGIT, i);
		}
		public WordContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_word; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterWord(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitWord(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitWord(this);
			else return visitor.visitChildren(this);
		}
	}

	public final WordContext word() throws RecognitionException {
		WordContext _localctx = new WordContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_word);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(50); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(49);
					match(LETTER_OR_DIGIT);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(52); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,7,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
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
	public static class TextContext extends ParserRuleContext {
		public List<TerminalNode> TEXT() { return getTokens(FileSourceShPreProcessorBraceParser.TEXT); }
		public TerminalNode TEXT(int i) {
			return getToken(FileSourceShPreProcessorBraceParser.TEXT, i);
		}
		public TextContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_text; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).enterText(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorBraceListener ) ((FileSourceShPreProcessorBraceListener)listener).exitText(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorBraceVisitor ) return ((FileSourceShPreProcessorBraceVisitor<? extends T>)visitor).visitText(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TextContext text() throws RecognitionException {
		TextContext _localctx = new TextContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_text);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(55); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(54);
					match(TEXT);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(57); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,8,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
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

	public static final String _serializedATN =
		"\u0004\u0001\b<\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000\u0001\u0000\u0005"+
		"\u0000\u0011\b\u0000\n\u0000\f\u0000\u0014\t\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0003\u0001\u001e\b\u0001\u0001\u0001\u0001\u0001\u0003\u0001\"\b\u0001"+
		"\u0001\u0002\u0003\u0002%\b\u0002\u0001\u0002\u0001\u0002\u0001\u0003"+
		"\u0001\u0003\u0003\u0003+\b\u0003\u0001\u0004\u0004\u0004.\b\u0004\u000b"+
		"\u0004\f\u0004/\u0001\u0005\u0004\u00053\b\u0005\u000b\u0005\f\u00054"+
		"\u0001\u0006\u0004\u00068\b\u0006\u000b\u0006\f\u00069\u0001\u0006\u0000"+
		"\u0000\u0007\u0000\u0002\u0004\u0006\b\n\f\u0000\u0000=\u0000\u0012\u0001"+
		"\u0000\u0000\u0000\u0002\u0017\u0001\u0000\u0000\u0000\u0004$\u0001\u0000"+
		"\u0000\u0000\u0006*\u0001\u0000\u0000\u0000\b-\u0001\u0000\u0000\u0000"+
		"\n2\u0001\u0000\u0000\u0000\f7\u0001\u0000\u0000\u0000\u000e\u0011\u0003"+
		"\f\u0006\u0000\u000f\u0011\u0003\u0002\u0001\u0000\u0010\u000e\u0001\u0000"+
		"\u0000\u0000\u0010\u000f\u0001\u0000\u0000\u0000\u0011\u0014\u0001\u0000"+
		"\u0000\u0000\u0012\u0010\u0001\u0000\u0000\u0000\u0012\u0013\u0001\u0000"+
		"\u0000\u0000\u0013\u0015\u0001\u0000\u0000\u0000\u0014\u0012\u0001\u0000"+
		"\u0000\u0000\u0015\u0016\u0005\u0000\u0000\u0001\u0016\u0001\u0001\u0000"+
		"\u0000\u0000\u0017\u0018\u0003\u0004\u0002\u0000\u0018\u0019\u0003\u0006"+
		"\u0003\u0000\u0019\u001a\u0005\u0005\u0000\u0000\u001a\u001d\u0003\u0006"+
		"\u0003\u0000\u001b\u001c\u0005\u0005\u0000\u0000\u001c\u001e\u0003\b\u0004"+
		"\u0000\u001d\u001b\u0001\u0000\u0000\u0000\u001d\u001e\u0001\u0000\u0000"+
		"\u0000\u001e\u001f\u0001\u0000\u0000\u0000\u001f!\u0005\u0004\u0000\u0000"+
		" \"\u0003\n\u0005\u0000! \u0001\u0000\u0000\u0000!\"\u0001\u0000\u0000"+
		"\u0000\"\u0003\u0001\u0000\u0000\u0000#%\u0003\n\u0005\u0000$#\u0001\u0000"+
		"\u0000\u0000$%\u0001\u0000\u0000\u0000%&\u0001\u0000\u0000\u0000&\'\u0005"+
		"\u0003\u0000\u0000\'\u0005\u0001\u0000\u0000\u0000(+\u0003\b\u0004\u0000"+
		")+\u0005\u0007\u0000\u0000*(\u0001\u0000\u0000\u0000*)\u0001\u0000\u0000"+
		"\u0000+\u0007\u0001\u0000\u0000\u0000,.\u0005\u0001\u0000\u0000-,\u0001"+
		"\u0000\u0000\u0000./\u0001\u0000\u0000\u0000/-\u0001\u0000\u0000\u0000"+
		"/0\u0001\u0000\u0000\u00000\t\u0001\u0000\u0000\u000013\u0005\u0002\u0000"+
		"\u000021\u0001\u0000\u0000\u000034\u0001\u0000\u0000\u000042\u0001\u0000"+
		"\u0000\u000045\u0001\u0000\u0000\u00005\u000b\u0001\u0000\u0000\u0000"+
		"68\u0005\u0006\u0000\u000076\u0001\u0000\u0000\u000089\u0001\u0000\u0000"+
		"\u000097\u0001\u0000\u0000\u00009:\u0001\u0000\u0000\u0000:\r\u0001\u0000"+
		"\u0000\u0000\t\u0010\u0012\u001d!$*/49";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}