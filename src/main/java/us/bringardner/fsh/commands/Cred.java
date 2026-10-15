package us.bringardner.fsh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import us.bringardner.fsh.ShellCommand;
import us.bringardner.fsh.ShellContext;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.credentials.CommandCredentialProvider;
import us.bringardner.parley.files.credentials.CredentialException;
import us.bringardner.parley.files.credentials.CredentialProvider;
import us.bringardner.parley.files.credentials.CredentialRef;
import us.bringardner.parley.files.credentials.CredentialRequest;
import us.bringardner.parley.files.credentials.Credentials;
import us.bringardner.parley.files.credentials.KeyStoreCredentialProvider;

/**
 * Manages the secrets that connect settings name by reference (password=@keychain:parley/ftp):
 * stores them in a credential store, checks and lists them, and never prints a secret.
 */
public class Cred extends ShellCommand {
	static String name = "cred";
	static String help = "Manage secrets that connect settings name by reference, such as password=@keychain:parley/ftp\n"
			+ "USAGE: cred set REF            store a secret; asks for it twice, without echo\n"
			+ "       cred get REF            say whether REF can be resolved (never prints the secret)\n"
			+ "       cred list [SCHEME]      the references a store holds (where it can list them)\n"
			+ "       cred rm REF             remove a secret\n"
			+ "       cred test FACTORY name=value ...  connect with those settings, then disconnect\n"
			+ "       cred providers          the credential stores and whether each works here\n"
			+ "       cred enable|disable cmd allow @cmd: references for this session (off by default)\n"
			+ "REF is scheme:locator, with or without the leading @: keychain:parley/ftp, keystore:prod-db,\n"
			+ "file:~/.parley/db.pw. The secret is never taken as an argument, so it stays out of the history.\n"
			+ "At a terminal a spinner turns as you type (the length doesn't show); set FSH_SECRET_MASK=* to see a * per\n"
			+ "character, or FSH_SECRET_MASK= (empty) to see nothing.";

	public Cred() {
		super(name, help);
	}

	@Override
	public int process(ShellContext ctx) throws IOException {
		List<String> a = new ArrayList<>();
		// (args holds the arguments only, not the command name)
		for(int i = 0; i < args.length; i++) {
			a.add(""+args[i].getValue(ctx));
		}
		if( a.isEmpty()) {
			ctx.stdout.println(help);
			return 2;
		}
		String sub = a.remove(0);
		try {
			switch (sub) {
			case "set": return set(ctx, a);
			case "get": return get(ctx, a);
			case "list": return list(ctx, a);
			case "rm": return rm(ctx, a);
			case "test": return test(ctx, a);
			case "providers": return providers(ctx);
			case "enable": return enable(ctx, a, true);
			case "disable": return enable(ctx, a, false);
			default:
				ctx.stderr.println(name+": unknown subcommand '"+sub+"'");
				ctx.stderr.println(help);
				return 2;
			}
		} catch (CredentialException e) {
			ctx.stderr.println(name+": "+e.getMessage());
			return 1;
		}
	}

	/** A reference from an argument: keychain:parley/ftp or @keychain:parley/ftp. */
	static CredentialRef ref(String arg) throws CredentialException {
		String v = arg.startsWith("@") ? arg : "@"+arg;
		Optional<CredentialRef> r = CredentialRef.parse(v);
		if( r.isEmpty()) {
			throw new CredentialException(null, "'"+arg+"' isn't a reference; use scheme:locator, such as keychain:parley/ftp");
		}
		return r.get();
	}

	private int usage(ShellContext ctx, String what) {
		ctx.stderr.println(name+": usage: cred "+what);
		return 2;
	}

	private int set(ShellContext ctx, List<String> a) throws IOException {
		if( a.size() != 1 ) {
			// a second argument would be the secret, which mustn't be typed on the command line
			return usage(ctx, "set REF   (the secret is asked for, never given as an argument)");
		}
		CredentialRef ref = ref(a.get(0));
		if( !CredentialPrompts.canAsk(ctx)) {
			ctx.stderr.println(name+": set asks for the secret: it needs an interactive shell, or a display for a password dialog");
			return 1;
		}
		// a keystore is unlocked (or its master password chosen) before the secret is typed,
		// so a mistyped master password doesn't waste it
		CredentialProvider store = Credentials.get(ref.scheme()).orElse(null);
		if( store instanceof KeyStoreCredentialProvider && store.isAvailable()) {
			CredentialPrompts.with(ctx, () -> {
				((KeyStoreCredentialProvider) store).prepare();
				return null;
			});
		}
				char[] first = CredentialPrompts.readSecret(ctx, "Secret for "+ref+": ");
		if( first == null || first.length == 0 ) {
			ctx.stderr.println(name+": nothing stored");
			return 1;
		}
		char[] again = CredentialPrompts.readSecret(ctx, "Again: ");
		try {
			if( again == null || !Arrays.equals(first, again)) {
				ctx.stderr.println(name+": they didn't match; nothing stored");
				return 1;
			}
			CredentialPrompts.with(ctx, () -> {
				Credentials.store(ref, first);
				return null;
			});
		} finally {
			Arrays.fill(first, '\0');
			if( again != null ) {
				Arrays.fill(again, '\0');
			}
		}
		ctx.stdout.println("Stored "+ref);
		return 0;
	}

	private int get(ShellContext ctx, List<String> a) throws IOException {
		if( a.size() != 1 ) {
			return usage(ctx, "get REF");
		}
		CredentialRef ref = ref(a.get(0));
		char[] secret = CredentialPrompts.with(ctx, () -> Credentials.resolve(ref, CredentialRequest.forProperty("")));
		Arrays.fill(secret, '\0');
		ctx.stdout.println(ref+": found");
		return 0;
	}

	private int list(ShellContext ctx, List<String> a) throws IOException {
		if( a.size() > 1 ) {
			return usage(ctx, "list [SCHEME]");
		}
		int ret = 0;
		for(CredentialProvider p : Credentials.providers()) {
			if( !a.isEmpty() && !a.get(0).equals(p.scheme())) {
				continue;
			}
			if( !p.isAvailable()) {
				continue;
			}
			try {
				for(CredentialRef r : CredentialPrompts.with(ctx, p::list)) {
					ctx.stdout.println(r);
				}
			} catch (CredentialException e) {
				ctx.stderr.println(name+": "+p.scheme()+": "+e.getMessage());
				ret = 1;
			}
		}
		return ret;
	}

	private int rm(ShellContext ctx, List<String> a) throws IOException {
		if( a.size() != 1 ) {
			return usage(ctx, "rm REF");
		}
		CredentialRef ref = ref(a.get(0));
		CredentialPrompts.with(ctx, () -> {
			Credentials.remove(ref);
			return null;
		});
		ctx.stdout.println("Removed "+ref);
		return 0;
	}

	private int test(ShellContext ctx, List<String> a) throws IOException {
		if( a.isEmpty()) {
			return usage(ctx, "test FACTORY name=value ...");
		}
		String id = a.remove(0);
		FileSourceFactory f = FileSourceFactory.getFileSourceFactory(id);
		if( f == null ) {
			ctx.stderr.println(name+": unknown factory id="+id);
			return 1;
		}
		Properties props = f.getConnectProperties();
		for(String s : a) {
			String[] parts = s.split("=", 2);
			if( parts.length != 2 || parts[0].isBlank()) {
				// only the name: the value may be a password
				ctx.stderr.println(name+": invalid setting (expected name=value): "+parts[0]);
				return 2;
			}
			props.setProperty(parts[0].trim(), parts[1].trim());
		}
		boolean ok = CredentialPrompts.with(ctx, () -> f.connect(props));
		try {
			if( !ok ) {
				ctx.stderr.println(name+": "+id+" could not connect");
				return 1;
			}
			ctx.stdout.println(id+": connected");
			return 0;
		} finally {
			try {
				f.disConnect();
			} catch (IOException e) {
				// the test is over either way
			}
		}
	}

	private int providers(ShellContext ctx) {
		for(CredentialProvider p : Credentials.providers()) {
			String state = p.isAvailable() ? (p.canStore() ? "available, can store" : "available, read only")
					: "unavailable: "+p.unavailableReason();
			ctx.stdout.println(p.scheme()+"\t"+state+"\t"+p.description());
		}
		if( Credentials.get("keychain").isEmpty()) {
			ctx.stdout.println("keychain\tnot installed: "+whyNoKeychain());
		}
		return 0;
	}

	/** Why this fsh has no @keychain: store, so a build that left it out is easy to spot. */
	static String whyNoKeychain() {
		if( System.getProperty("org.graalvm.nativeimage.imagecode") != null ) {
			return "this is the native fsh, which is built without it; run the Java fsh (fsh.jar) for @keychain:";
		}
		if( Runtime.version().feature() < 17 ) {
			return "it needs Java 17 or newer, and this is Java "+Runtime.version().feature();
		}
		return "parley-credentials-keychain isn't on the classpath; build fsh with Maven (not with -Dfsh.noKeychain),"
				+ " or refresh the IDE's Maven dependencies before exporting a jar";
	}

	private int enable(ShellContext ctx, List<String> a, boolean on) {
		if( a.size() != 1 || !a.get(0).equals("cmd")) {
			return usage(ctx, (on ? "enable" : "disable")+" cmd");
		}
		CommandCredentialProvider.setEnabled(on);
		ctx.stdout.println("@cmd: references are "+(on ? "on" : "off")+" for this session");
		return 0;
	}
}
