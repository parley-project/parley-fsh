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
		T__9=10, T__10=11, T__11=12, PPID=13, PPDIGIT=14, PPTAG=15, PPNL=16, PPESC=17, 
		PPTEXT=18, WS=19;
	public static final int
		RULE_ppcode = 0, RULE_ppescape = 1, RULE_ppexpr = 2, RULE_ppcommand = 3, 
		RULE_pp_backtick_command = 4, RULE_pp_dollar_command = 5, RULE_pp_nested = 6, 
		RULE_pp_dq = 7, RULE_pp_parameter = 8, RULE_ppvariable = 9, RULE_pptext = 10;
	private static String[] makeRuleNames() {
		return new String[] {
			"ppcode", "ppescape", "ppexpr", "ppcommand", "pp_backtick_command", "pp_dollar_command", 
			"pp_nested", "pp_dq", "pp_parameter", "ppvariable", "pptext"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'$(('", "')'", "'`'", "'$('", "'('", "'\"'", "'''", "'${'", "'}'", 
			"'$'", "'?'", "'*'", null, null, null, "'\\n'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, "PPID", "PPDIGIT", "PPTAG", "PPNL", "PPESC", "PPTEXT", "WS"
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
			setState(31);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 468478L) != 0)) {
				{
				setState(29);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__2:
				case T__3:
					{
					setState(22);
					ppcommand();
					}
					break;
				case T__0:
					{
					setState(23);
					ppexpr();
					}
					break;
				case T__9:
					{
					setState(24);
					ppvariable();
					}
					break;
				case T__1:
				case T__4:
				case T__5:
				case T__6:
				case PPNL:
				case PPTEXT:
					{
					setState(25);
					pptext();
					}
					break;
				case T__7:
					{
					setState(26);
					pp_parameter();
					}
					break;
				case PPESC:
					{
					setState(27);
					ppescape();
					}
					break;
				case PPID:
					{
					setState(28);
					match(PPID);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(33);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(34);
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
			setState(36);
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
		public List<Pp_nestedContext> pp_nested() {
			return getRuleContexts(Pp_nestedContext.class);
		}
		public Pp_nestedContext pp_nested(int i) {
			return getRuleContext(Pp_nestedContext.class,i);
		}
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
			setState(38);
			match(T__0);
			setState(42);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048570L) != 0)) {
				{
				{
				setState(39);
				pp_nested();
				}
				}
				setState(44);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(45);
			match(T__1);
			setState(46);
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
			setState(50);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
				enterOuterAlt(_localctx, 1);
				{
				setState(48);
				pp_backtick_command();
				}
				break;
			case T__3:
				enterOuterAlt(_localctx, 2);
				{
				setState(49);
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
			setState(52);
			match(T__2);
			setState(56);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048566L) != 0)) {
				{
				{
				setState(53);
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
				setState(58);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(59);
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
		public List<Pp_nestedContext> pp_nested() {
			return getRuleContexts(Pp_nestedContext.class);
		}
		public Pp_nestedContext pp_nested(int i) {
			return getRuleContext(Pp_nestedContext.class,i);
		}
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
			setState(61);
			match(T__3);
			setState(65);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048570L) != 0)) {
				{
				{
				setState(62);
				pp_nested();
				}
				}
				setState(67);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(68);
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
	public static class Pp_nestedContext extends ParserRuleContext {
		public List<Pp_nestedContext> pp_nested() {
			return getRuleContexts(Pp_nestedContext.class);
		}
		public Pp_nestedContext pp_nested(int i) {
			return getRuleContext(Pp_nestedContext.class,i);
		}
		public List<Pp_dqContext> pp_dq() {
			return getRuleContexts(Pp_dqContext.class);
		}
		public Pp_dqContext pp_dq(int i) {
			return getRuleContext(Pp_dqContext.class,i);
		}
		public Pp_nestedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pp_nested; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPp_nested(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPp_nested(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPp_nested(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Pp_nestedContext pp_nested() throws RecognitionException {
		Pp_nestedContext _localctx = new Pp_nestedContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_pp_nested);
		int _la;
		try {
			setState(104);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case PPID:
			case PPDIGIT:
			case PPTAG:
			case PPNL:
			case PPESC:
			case PPTEXT:
			case WS:
				enterOuterAlt(_localctx, 1);
				{
				setState(70);
				_la = _input.LA(1);
				if ( _la <= 0 || ((((_la) & ~0x3f) == 0 && ((1L << _la) & 246L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case T__3:
			case T__4:
				enterOuterAlt(_localctx, 2);
				{
				setState(71);
				_la = _input.LA(1);
				if ( !(_la==T__3 || _la==T__4) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(75);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048570L) != 0)) {
					{
					{
					setState(72);
					pp_nested();
					}
					}
					setState(77);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(78);
				match(T__1);
				}
				break;
			case T__0:
				enterOuterAlt(_localctx, 3);
				{
				setState(79);
				match(T__0);
				setState(83);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048570L) != 0)) {
					{
					{
					setState(80);
					pp_nested();
					}
					}
					setState(85);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(86);
				match(T__1);
				setState(87);
				match(T__1);
				}
				break;
			case T__5:
				enterOuterAlt(_localctx, 4);
				{
				setState(88);
				match(T__5);
				setState(92);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048510L) != 0)) {
					{
					{
					setState(89);
					pp_dq();
					}
					}
					setState(94);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(95);
				match(T__5);
				}
				break;
			case T__6:
				enterOuterAlt(_localctx, 5);
				{
				setState(96);
				match(T__6);
				setState(100);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048446L) != 0)) {
					{
					{
					setState(97);
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
					setState(102);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(103);
				match(T__6);
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
	public static class Pp_dqContext extends ParserRuleContext {
		public List<Pp_nestedContext> pp_nested() {
			return getRuleContexts(Pp_nestedContext.class);
		}
		public Pp_nestedContext pp_nested(int i) {
			return getRuleContext(Pp_nestedContext.class,i);
		}
		public Pp_dqContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pp_dq; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).enterPp_dq(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FileSourceShPreProcessorListener ) ((FileSourceShPreProcessorListener)listener).exitPp_dq(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FileSourceShPreProcessorVisitor ) return ((FileSourceShPreProcessorVisitor<? extends T>)visitor).visitPp_dq(this);
			else return visitor.visitChildren(this);
		}
	}

	public final Pp_dqContext pp_dq() throws RecognitionException {
		Pp_dqContext _localctx = new Pp_dqContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_pp_dq);
		int _la;
		try {
			setState(124);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
			case T__2:
			case T__4:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case PPID:
			case PPDIGIT:
			case PPTAG:
			case PPNL:
			case PPESC:
			case PPTEXT:
			case WS:
				enterOuterAlt(_localctx, 1);
				{
				setState(106);
				_la = _input.LA(1);
				if ( _la <= 0 || ((((_la) & ~0x3f) == 0 && ((1L << _la) & 82L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case T__3:
				enterOuterAlt(_localctx, 2);
				{
				setState(107);
				match(T__3);
				setState(111);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048570L) != 0)) {
					{
					{
					setState(108);
					pp_nested();
					}
					}
					setState(113);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(114);
				match(T__1);
				}
				break;
			case T__0:
				enterOuterAlt(_localctx, 3);
				{
				setState(115);
				match(T__0);
				setState(119);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048570L) != 0)) {
					{
					{
					setState(116);
					pp_nested();
					}
					}
					setState(121);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(122);
				match(T__1);
				setState(123);
				match(T__1);
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
		enterRule(_localctx, 16, RULE_pp_parameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(126);
			match(T__7);
			setState(130);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 1048062L) != 0)) {
				{
				{
				setState(127);
				_la = _input.LA(1);
				if ( _la <= 0 || (_la==T__8) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				}
				setState(132);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(133);
			match(T__8);
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
		enterRule(_localctx, 18, RULE_ppvariable);
		int _la;
		try {
			setState(139);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(135);
				match(T__9);
				setState(136);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 56320L) != 0)) ) {
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
				setState(137);
				match(T__9);
				setState(138);
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
		enterRule(_localctx, 20, RULE_pptext);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(142); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(141);
					_la = _input.LA(1);
					if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 327908L) != 0)) ) {
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
				setState(144); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,16,_ctx);
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
		"\u0004\u0001\u0013\u0093\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0005\u0000"+
		"\u001e\b\u0000\n\u0000\f\u0000!\t\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0005\u0002)\b\u0002\n\u0002"+
		"\f\u0002,\t\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0003\u0001"+
		"\u0003\u0003\u00033\b\u0003\u0001\u0004\u0001\u0004\u0005\u00047\b\u0004"+
		"\n\u0004\f\u0004:\t\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005"+
		"\u0005\u0005@\b\u0005\n\u0005\f\u0005C\t\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0005\u0006J\b\u0006\n\u0006\f\u0006"+
		"M\t\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0005\u0006R\b\u0006\n\u0006"+
		"\f\u0006U\t\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0005"+
		"\u0006[\b\u0006\n\u0006\f\u0006^\t\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0005\u0006c\b\u0006\n\u0006\f\u0006f\t\u0006\u0001\u0006\u0003"+
		"\u0006i\b\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0005\u0007n\b\u0007"+
		"\n\u0007\f\u0007q\t\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0005\u0007"+
		"v\b\u0007\n\u0007\f\u0007y\t\u0007\u0001\u0007\u0001\u0007\u0003\u0007"+
		"}\b\u0007\u0001\b\u0001\b\u0005\b\u0081\b\b\n\b\f\b\u0084\t\b\u0001\b"+
		"\u0001\b\u0001\t\u0001\t\u0001\t\u0001\t\u0003\t\u008c\b\t\u0001\n\u0004"+
		"\n\u008f\b\n\u000b\n\f\n\u0090\u0001\n\u0000\u0000\u000b\u0000\u0002\u0004"+
		"\u0006\b\n\f\u000e\u0010\u0012\u0014\u0000\b\u0001\u0000\u0003\u0003\u0002"+
		"\u0000\u0001\u0002\u0004\u0007\u0001\u0000\u0004\u0005\u0001\u0000\u0007"+
		"\u0007\u0003\u0000\u0001\u0001\u0004\u0004\u0006\u0006\u0001\u0000\t\t"+
		"\u0002\u0000\n\f\u000e\u000f\u0004\u0000\u0002\u0002\u0005\u0007\u0010"+
		"\u0010\u0012\u0012\u00a1\u0000\u001f\u0001\u0000\u0000\u0000\u0002$\u0001"+
		"\u0000\u0000\u0000\u0004&\u0001\u0000\u0000\u0000\u00062\u0001\u0000\u0000"+
		"\u0000\b4\u0001\u0000\u0000\u0000\n=\u0001\u0000\u0000\u0000\fh\u0001"+
		"\u0000\u0000\u0000\u000e|\u0001\u0000\u0000\u0000\u0010~\u0001\u0000\u0000"+
		"\u0000\u0012\u008b\u0001\u0000\u0000\u0000\u0014\u008e\u0001\u0000\u0000"+
		"\u0000\u0016\u001e\u0003\u0006\u0003\u0000\u0017\u001e\u0003\u0004\u0002"+
		"\u0000\u0018\u001e\u0003\u0012\t\u0000\u0019\u001e\u0003\u0014\n\u0000"+
		"\u001a\u001e\u0003\u0010\b\u0000\u001b\u001e\u0003\u0002\u0001\u0000\u001c"+
		"\u001e\u0005\r\u0000\u0000\u001d\u0016\u0001\u0000\u0000\u0000\u001d\u0017"+
		"\u0001\u0000\u0000\u0000\u001d\u0018\u0001\u0000\u0000\u0000\u001d\u0019"+
		"\u0001\u0000\u0000\u0000\u001d\u001a\u0001\u0000\u0000\u0000\u001d\u001b"+
		"\u0001\u0000\u0000\u0000\u001d\u001c\u0001\u0000\u0000\u0000\u001e!\u0001"+
		"\u0000\u0000\u0000\u001f\u001d\u0001\u0000\u0000\u0000\u001f \u0001\u0000"+
		"\u0000\u0000 \"\u0001\u0000\u0000\u0000!\u001f\u0001\u0000\u0000\u0000"+
		"\"#\u0005\u0000\u0000\u0001#\u0001\u0001\u0000\u0000\u0000$%\u0005\u0011"+
		"\u0000\u0000%\u0003\u0001\u0000\u0000\u0000&*\u0005\u0001\u0000\u0000"+
		"\')\u0003\f\u0006\u0000(\'\u0001\u0000\u0000\u0000),\u0001\u0000\u0000"+
		"\u0000*(\u0001\u0000\u0000\u0000*+\u0001\u0000\u0000\u0000+-\u0001\u0000"+
		"\u0000\u0000,*\u0001\u0000\u0000\u0000-.\u0005\u0002\u0000\u0000./\u0005"+
		"\u0002\u0000\u0000/\u0005\u0001\u0000\u0000\u000003\u0003\b\u0004\u0000"+
		"13\u0003\n\u0005\u000020\u0001\u0000\u0000\u000021\u0001\u0000\u0000\u0000"+
		"3\u0007\u0001\u0000\u0000\u000048\u0005\u0003\u0000\u000057\b\u0000\u0000"+
		"\u000065\u0001\u0000\u0000\u00007:\u0001\u0000\u0000\u000086\u0001\u0000"+
		"\u0000\u000089\u0001\u0000\u0000\u00009;\u0001\u0000\u0000\u0000:8\u0001"+
		"\u0000\u0000\u0000;<\u0005\u0003\u0000\u0000<\t\u0001\u0000\u0000\u0000"+
		"=A\u0005\u0004\u0000\u0000>@\u0003\f\u0006\u0000?>\u0001\u0000\u0000\u0000"+
		"@C\u0001\u0000\u0000\u0000A?\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000"+
		"\u0000BD\u0001\u0000\u0000\u0000CA\u0001\u0000\u0000\u0000DE\u0005\u0002"+
		"\u0000\u0000E\u000b\u0001\u0000\u0000\u0000Fi\b\u0001\u0000\u0000GK\u0007"+
		"\u0002\u0000\u0000HJ\u0003\f\u0006\u0000IH\u0001\u0000\u0000\u0000JM\u0001"+
		"\u0000\u0000\u0000KI\u0001\u0000\u0000\u0000KL\u0001\u0000\u0000\u0000"+
		"LN\u0001\u0000\u0000\u0000MK\u0001\u0000\u0000\u0000Ni\u0005\u0002\u0000"+
		"\u0000OS\u0005\u0001\u0000\u0000PR\u0003\f\u0006\u0000QP\u0001\u0000\u0000"+
		"\u0000RU\u0001\u0000\u0000\u0000SQ\u0001\u0000\u0000\u0000ST\u0001\u0000"+
		"\u0000\u0000TV\u0001\u0000\u0000\u0000US\u0001\u0000\u0000\u0000VW\u0005"+
		"\u0002\u0000\u0000Wi\u0005\u0002\u0000\u0000X\\\u0005\u0006\u0000\u0000"+
		"Y[\u0003\u000e\u0007\u0000ZY\u0001\u0000\u0000\u0000[^\u0001\u0000\u0000"+
		"\u0000\\Z\u0001\u0000\u0000\u0000\\]\u0001\u0000\u0000\u0000]_\u0001\u0000"+
		"\u0000\u0000^\\\u0001\u0000\u0000\u0000_i\u0005\u0006\u0000\u0000`d\u0005"+
		"\u0007\u0000\u0000ac\b\u0003\u0000\u0000ba\u0001\u0000\u0000\u0000cf\u0001"+
		"\u0000\u0000\u0000db\u0001\u0000\u0000\u0000de\u0001\u0000\u0000\u0000"+
		"eg\u0001\u0000\u0000\u0000fd\u0001\u0000\u0000\u0000gi\u0005\u0007\u0000"+
		"\u0000hF\u0001\u0000\u0000\u0000hG\u0001\u0000\u0000\u0000hO\u0001\u0000"+
		"\u0000\u0000hX\u0001\u0000\u0000\u0000h`\u0001\u0000\u0000\u0000i\r\u0001"+
		"\u0000\u0000\u0000j}\b\u0004\u0000\u0000ko\u0005\u0004\u0000\u0000ln\u0003"+
		"\f\u0006\u0000ml\u0001\u0000\u0000\u0000nq\u0001\u0000\u0000\u0000om\u0001"+
		"\u0000\u0000\u0000op\u0001\u0000\u0000\u0000pr\u0001\u0000\u0000\u0000"+
		"qo\u0001\u0000\u0000\u0000r}\u0005\u0002\u0000\u0000sw\u0005\u0001\u0000"+
		"\u0000tv\u0003\f\u0006\u0000ut\u0001\u0000\u0000\u0000vy\u0001\u0000\u0000"+
		"\u0000wu\u0001\u0000\u0000\u0000wx\u0001\u0000\u0000\u0000xz\u0001\u0000"+
		"\u0000\u0000yw\u0001\u0000\u0000\u0000z{\u0005\u0002\u0000\u0000{}\u0005"+
		"\u0002\u0000\u0000|j\u0001\u0000\u0000\u0000|k\u0001\u0000\u0000\u0000"+
		"|s\u0001\u0000\u0000\u0000}\u000f\u0001\u0000\u0000\u0000~\u0082\u0005"+
		"\b\u0000\u0000\u007f\u0081\b\u0005\u0000\u0000\u0080\u007f\u0001\u0000"+
		"\u0000\u0000\u0081\u0084\u0001\u0000\u0000\u0000\u0082\u0080\u0001\u0000"+
		"\u0000\u0000\u0082\u0083\u0001\u0000\u0000\u0000\u0083\u0085\u0001\u0000"+
		"\u0000\u0000\u0084\u0082\u0001\u0000\u0000\u0000\u0085\u0086\u0005\t\u0000"+
		"\u0000\u0086\u0011\u0001\u0000\u0000\u0000\u0087\u0088\u0005\n\u0000\u0000"+
		"\u0088\u008c\u0007\u0006\u0000\u0000\u0089\u008a\u0005\n\u0000\u0000\u008a"+
		"\u008c\u0005\r\u0000\u0000\u008b\u0087\u0001\u0000\u0000\u0000\u008b\u0089"+
		"\u0001\u0000\u0000\u0000\u008c\u0013\u0001\u0000\u0000\u0000\u008d\u008f"+
		"\u0007\u0007\u0000\u0000\u008e\u008d\u0001\u0000\u0000\u0000\u008f\u0090"+
		"\u0001\u0000\u0000\u0000\u0090\u008e\u0001\u0000\u0000\u0000\u0090\u0091"+
		"\u0001\u0000\u0000\u0000\u0091\u0015\u0001\u0000\u0000\u0000\u0011\u001d"+
		"\u001f*28AKS\\dhow|\u0082\u008b\u0090";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}