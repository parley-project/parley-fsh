// Generated from FileSourceShPreProcessor.g4 by ANTLR 4.13.2
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
public class FileSourceShPreProcessorParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, PPID=11, PPDIGIT=12, PPTAG=13, PPNL=14, PPESC=15, PPTEXT=16, 
		WS=17;
	public static final int
		RULE_ppcode = 0, RULE_ppescape = 1, RULE_ppexpr = 2, RULE_ppcommand = 3, 
		RULE_pp_backtick_command = 4, RULE_pp_dollar_command = 5, RULE_pp_parameter = 6, 
		RULE_ppvariable = 7, RULE_pptext = 8;
	private static String[] makeRuleNames() {
		return new String[] {
			"ppcode", "ppescape", "ppexpr", "ppcommand", "pp_backtick_command", "pp_dollar_command", 
			"pp_parameter", "ppvariable", "pptext"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'$(('", "'))'", "'`'", "'$('", "')'", "'${'", "'}'", "'$'", "'?'", 
			"'*'", null, null, null, "'\\n'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, "PPID", 
			"PPDIGIT", "PPTAG", "PPNL", "PPESC", "PPTEXT", "WS"
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
	public String getGrammarFileName() { return "FileSourceShPreProcessor.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public FileSourceShPreProcessorParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class PpcodeContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(FileSourceShPreProcessorParser.EOF, 0); }
		public List<PpcommandContext> ppcommand() {
			return getRuleContexts(PpcommandContext.class);
		}
		public PpcommandContext ppcommand(int i) {
			return getRuleContext(PpcommandContext.class,i);
		}
		public List<PpexprContext> ppexpr() {
			return getRuleContexts(PpexprContext.class);
		}
		public PpexprContext ppexpr(int i) {
			return getRuleContext(PpexprContext.class,i);
		}
		public List<PpvariableContext> ppvariable() {
			return getRuleContexts(PpvariableContext.class);
		}
		public PpvariableContext ppvariable(int i) {
			return getRuleContext(PpvariableContext.class,i);
		}
		public List<PptextContext> pptext() {
			return getRuleContexts(PptextContext.class);
		}
		public PptextContext pptext(int i) {
			return getRuleContext(PptextContext.class,i);
		}
		public List<Pp_parameterContext> pp_parameter() {
			return getRuleContexts(Pp_parameterContext.class);
		}
		public Pp_parameterContext pp_parameter(int i) {
			return getRuleContext(Pp_parameterContext.class,i);
		}
		public List<PpescapeContext> ppescape() {
			return getRuleContexts(PpescapeContext.class);
		}
		public PpescapeContext ppescape(int i) {
			return getRuleContext(PpescapeContext.class,i);
		}
		public List<TerminalNode> PPID() { return getTokens(FileSourceShPreProcessorParser.PPID); }
		public TerminalNode PPID(int i) {
			return getToken(FileSourceShPreProcessorParser.PPID, i);
		}
		public PpcodeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ppcode; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPpcode(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPpcode(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPpcode(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PpcodeContext ppcode() throws RecognitionException {
		PpcodeContext _localctx = new PpcodeContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_ppcode);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(27);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 117082L) != 0)) {
				{
				setState(25);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__2:
				case T__3:
					{
					setState(18);
					ppcommand();
					}
					break;
				case T__0:
					{
					setState(19);
					ppexpr();
					}
					break;
				case T__7:
					{
					setState(20);
					ppvariable();
					}
					break;
				case PPNL:
				case PPTEXT:
					{
					setState(21);
					pptext();
					}
					break;
				case T__5:
					{
					setState(22);
					pp_parameter();
					}
					break;
				case PPESC:
					{
					setState(23);
					ppescape();
					}
					break;
				case PPID:
					{
					setState(24);
					match(PPID);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(29);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(30);
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
	public static class PpescapeContext extends ParserRuleContext {
		public TerminalNode PPESC() { return getToken(FileSourceShPreProcessorParser.PPESC, 0); }
		public PpescapeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ppescape; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPpescape(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPpescape(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPpescape(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PpescapeContext ppescape() throws RecognitionException {
		PpescapeContext _localctx = new PpescapeContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_ppescape);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(32);
			match(PPESC);
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
	public static class PpexprContext extends ParserRuleContext {
		public PpexprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ppexpr; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPpexpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPpexpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPpexpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PpexprContext ppexpr() throws RecognitionException {
		PpexprContext _localctx = new PpexprContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_ppexpr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(34);
			match(T__0);
			setState(38);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 262138L) != 0)) {
				{
				{
				setState(35);
				_la = _input.LA(1);
				if ( _la <= 0 || (_la==T__1) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				setState(40);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(41);
			match(T__1);
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
	public static class PpcommandContext extends ParserRuleContext {
		public Pp_backtick_commandContext pp_backtick_command() {
			return getRuleContext(Pp_backtick_commandContext.class,0);
		}
		public Pp_dollar_commandContext pp_dollar_command() {
			return getRuleContext(Pp_dollar_commandContext.class,0);
		}
		public PpcommandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ppcommand; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPpcommand(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPpcommand(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPpcommand(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PpcommandContext ppcommand() throws RecognitionException {
		PpcommandContext _localctx = new PpcommandContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_ppcommand);
		try {
			setState(45);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
				enterOuterAlt(_localctx, 1);
				{
				setState(43);
				pp_backtick_command();
				}
				break;
			case T__3:
				enterOuterAlt(_localctx, 2);
				{
				setState(44);
				pp_dollar_command();
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
	public static class Pp_backtick_commandContext extends ParserRuleContext {
		public Pp_backtick_commandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pp_backtick_command; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPp_backtick_command(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPp_backtick_command(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPp_backtick_command(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Pp_backtick_commandContext pp_backtick_command() throws RecognitionException {
		Pp_backtick_commandContext _localctx = new Pp_backtick_commandContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_pp_backtick_command);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(47);
			match(T__2);
			setState(51);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 262134L) != 0)) {
				{
				{
				setState(48);
				_la = _input.LA(1);
				if ( _la <= 0 || (_la==T__2) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				setState(53);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(54);
			match(T__2);
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
	public static class Pp_dollar_commandContext extends ParserRuleContext {
		public Pp_dollar_commandContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pp_dollar_command; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPp_dollar_command(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPp_dollar_command(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPp_dollar_command(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Pp_dollar_commandContext pp_dollar_command() throws RecognitionException {
		Pp_dollar_commandContext _localctx = new Pp_dollar_commandContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_pp_dollar_command);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(56);
			match(T__3);
			setState(60);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 262110L) != 0)) {
				{
				{
				setState(57);
				_la = _input.LA(1);
				if ( _la <= 0 || (_la==T__4) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				setState(62);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(63);
			match(T__4);
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
	public static class Pp_parameterContext extends ParserRuleContext {
		public Pp_parameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pp_parameter; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPp_parameter(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPp_parameter(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPp_parameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Pp_parameterContext pp_parameter() throws RecognitionException {
		Pp_parameterContext _localctx = new Pp_parameterContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_pp_parameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(65);
			match(T__5);
			setState(69);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 262014L) != 0)) {
				{
				{
				setState(66);
				_la = _input.LA(1);
				if ( _la <= 0 || (_la==T__6) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				setState(71);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(72);
			match(T__6);
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
	public static class PpvariableContext extends ParserRuleContext {
		public TerminalNode PPTAG() { return getToken(FileSourceShPreProcessorParser.PPTAG, 0); }
		public TerminalNode PPDIGIT() { return getToken(FileSourceShPreProcessorParser.PPDIGIT, 0); }
		public TerminalNode PPID() { return getToken(FileSourceShPreProcessorParser.PPID, 0); }
		public PpvariableContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ppvariable; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPpvariable(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPpvariable(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPpvariable(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PpvariableContext ppvariable() throws RecognitionException {
		PpvariableContext _localctx = new PpvariableContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_ppvariable);
		int _la;
		try {
			setState(78);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(74);
				match(T__7);
				setState(75);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 14080L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(76);
				match(T__7);
				setState(77);
				match(PPID);
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
	public static class PptextContext extends ParserRuleContext {
		public List<TerminalNode> PPTEXT() { return getTokens(FileSourceShPreProcessorParser.PPTEXT); }
		public TerminalNode PPTEXT(int i) {
			return getToken(FileSourceShPreProcessorParser.PPTEXT, i);
		}
		public List<TerminalNode> PPNL() { return getTokens(FileSourceShPreProcessorParser.PPNL); }
		public TerminalNode PPNL(int i) {
			return getToken(FileSourceShPreProcessorParser.PPNL, i);
		}
		public PptextContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pptext; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPptext(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPptext(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPptext(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PptextContext pptext() throws RecognitionException {
		PptextContext _localctx = new PptextContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_pptext);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(81); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(80);
					_la = _input.LA(1);
					if ( !(_la==PPNL || _la==PPTEXT) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(83); 
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
		"\u0004\u0001\u0011V\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0005\u0000\u001a\b\u0000\n\u0000\f\u0000\u001d"+
		"\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0002\u0001"+
		"\u0002\u0005\u0002%\b\u0002\n\u0002\f\u0002(\t\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0003\u0001\u0003\u0003\u0003.\b\u0003\u0001\u0004\u0001"+
		"\u0004\u0005\u00042\b\u0004\n\u0004\f\u00045\t\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0005\u0001\u0005\u0005\u0005;\b\u0005\n\u0005\f\u0005>\t"+
		"\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0005\u0006D\b"+
		"\u0006\n\u0006\f\u0006G\t\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0003\u0007O\b\u0007\u0001\b\u0004\bR\b"+
		"\b\u000b\b\f\bS\u0001\b\u0000\u0000\t\u0000\u0002\u0004\u0006\b\n\f\u000e"+
		"\u0010\u0000\u0006\u0001\u0000\u0002\u0002\u0001\u0000\u0003\u0003\u0001"+
		"\u0000\u0005\u0005\u0001\u0000\u0007\u0007\u0002\u0000\b\n\f\r\u0002\u0000"+
		"\u000e\u000e\u0010\u0010Z\u0000\u001b\u0001\u0000\u0000\u0000\u0002 \u0001"+
		"\u0000\u0000\u0000\u0004\"\u0001\u0000\u0000\u0000\u0006-\u0001\u0000"+
		"\u0000\u0000\b/\u0001\u0000\u0000\u0000\n8\u0001\u0000\u0000\u0000\fA"+
		"\u0001\u0000\u0000\u0000\u000eN\u0001\u0000\u0000\u0000\u0010Q\u0001\u0000"+
		"\u0000\u0000\u0012\u001a\u0003\u0006\u0003\u0000\u0013\u001a\u0003\u0004"+
		"\u0002\u0000\u0014\u001a\u0003\u000e\u0007\u0000\u0015\u001a\u0003\u0010"+
		"\b\u0000\u0016\u001a\u0003\f\u0006\u0000\u0017\u001a\u0003\u0002\u0001"+
		"\u0000\u0018\u001a\u0005\u000b\u0000\u0000\u0019\u0012\u0001\u0000\u0000"+
		"\u0000\u0019\u0013\u0001\u0000\u0000\u0000\u0019\u0014\u0001\u0000\u0000"+
		"\u0000\u0019\u0015\u0001\u0000\u0000\u0000\u0019\u0016\u0001\u0000\u0000"+
		"\u0000\u0019\u0017\u0001\u0000\u0000\u0000\u0019\u0018\u0001\u0000\u0000"+
		"\u0000\u001a\u001d\u0001\u0000\u0000\u0000\u001b\u0019\u0001\u0000\u0000"+
		"\u0000\u001b\u001c\u0001\u0000\u0000\u0000\u001c\u001e\u0001\u0000\u0000"+
		"\u0000\u001d\u001b\u0001\u0000\u0000\u0000\u001e\u001f\u0005\u0000\u0000"+
		"\u0001\u001f\u0001\u0001\u0000\u0000\u0000 !\u0005\u000f\u0000\u0000!"+
		"\u0003\u0001\u0000\u0000\u0000\"&\u0005\u0001\u0000\u0000#%\b\u0000\u0000"+
		"\u0000$#\u0001\u0000\u0000\u0000%(\u0001\u0000\u0000\u0000&$\u0001\u0000"+
		"\u0000\u0000&\'\u0001\u0000\u0000\u0000\')\u0001\u0000\u0000\u0000(&\u0001"+
		"\u0000\u0000\u0000)*\u0005\u0002\u0000\u0000*\u0005\u0001\u0000\u0000"+
		"\u0000+.\u0003\b\u0004\u0000,.\u0003\n\u0005\u0000-+\u0001\u0000\u0000"+
		"\u0000-,\u0001\u0000\u0000\u0000.\u0007\u0001\u0000\u0000\u0000/3\u0005"+
		"\u0003\u0000\u000002\b\u0001\u0000\u000010\u0001\u0000\u0000\u000025\u0001"+
		"\u0000\u0000\u000031\u0001\u0000\u0000\u000034\u0001\u0000\u0000\u0000"+
		"46\u0001\u0000\u0000\u000053\u0001\u0000\u0000\u000067\u0005\u0003\u0000"+
		"\u00007\t\u0001\u0000\u0000\u00008<\u0005\u0004\u0000\u00009;\b\u0002"+
		"\u0000\u0000:9\u0001\u0000\u0000\u0000;>\u0001\u0000\u0000\u0000<:\u0001"+
		"\u0000\u0000\u0000<=\u0001\u0000\u0000\u0000=?\u0001\u0000\u0000\u0000"+
		"><\u0001\u0000\u0000\u0000?@\u0005\u0005\u0000\u0000@\u000b\u0001\u0000"+
		"\u0000\u0000AE\u0005\u0006\u0000\u0000BD\b\u0003\u0000\u0000CB\u0001\u0000"+
		"\u0000\u0000DG\u0001\u0000\u0000\u0000EC\u0001\u0000\u0000\u0000EF\u0001"+
		"\u0000\u0000\u0000FH\u0001\u0000\u0000\u0000GE\u0001\u0000\u0000\u0000"+
		"HI\u0005\u0007\u0000\u0000I\r\u0001\u0000\u0000\u0000JK\u0005\b\u0000"+
		"\u0000KO\u0007\u0004\u0000\u0000LM\u0005\b\u0000\u0000MO\u0005\u000b\u0000"+
		"\u0000NJ\u0001\u0000\u0000\u0000NL\u0001\u0000\u0000\u0000O\u000f\u0001"+
		"\u0000\u0000\u0000PR\u0007\u0005\u0000\u0000QP\u0001\u0000\u0000\u0000"+
		"RS\u0001\u0000\u0000\u0000SQ\u0001\u0000\u0000\u0000ST\u0001\u0000\u0000"+
		"\u0000T\u0011\u0001\u0000\u0000\u0000\t\u0019\u001b&-3<ENS";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}