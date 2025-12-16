package us.bringardner.shell;

import java.awt.Component;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

public class VirtualFileSourceFactory extends FileSourceFactory {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final String TYPE_ID = "virtual";
	FileSource [] roots;
	FileSource firstRoot;
	FileSource currentDirectory;

	public VirtualFileSourceFactory() throws IOException {
		FileSourceFactory factory = FileSourceFactory.getDefaultFactory();
		roots = factory.listRoots();
		firstRoot = roots[0];
		currentDirectory = factory.getCurrentDirectory();
	}
	
	public VirtualFileSourceFactory(FileSource primary) throws IOException {
		firstRoot = primary;
		roots=new FileSource[1];
		roots[0] = primary;
		currentDirectory = primary.getFileSourceFactory().getCurrentDirectory();
	}

	public boolean mount(String mountPoint,FileSource dir) throws IOException {
		if( !dir.exists() || !dir.isDirectory()) {
			return false;
		}
		
		RootFile newRoot = new RootFile(mountPoint, dir, firstRoot);
		FileSource [] tmp = new FileSource[roots.length+1];
		String path = newRoot.getAbsolutePath();
		for (int idx = 0; idx < roots.length; idx++) {
			if( path.equals(roots[idx].getAbsolutePath())) {
				return false;
			}
			tmp[idx]=roots[idx];			
		}
		tmp[roots.length] = newRoot;
		// sort the roots so the longest paths are first
		Arrays.sort(tmp, new Comparator<FileSource>() {
			@Override
			public int compare(FileSource o1, FileSource o2) {
				return o2.getAbsolutePath().length()-o1.getAbsolutePath().length();
			}
		});
		roots = tmp;
		return true;
	}

	@Override
	public FileSource[] listRoots() throws IOException {
		return roots;
	}

	@Override
	public FileSource getCurrentDirectory() throws IOException {
		return currentDirectory;
	}

	@Override
	public boolean isVersionSupported() {
		return false;
	}

	@Override
	public void setCurrentDirectory(FileSource dir) throws IOException {
		currentDirectory = dir;
	}

	@Override
	public FileSource createFileSource(String fullPath) throws IOException {

		boolean abs = fullPath.startsWith("/");
		if( isWindows()) {
			abs = fullPath.startsWith("/") || ( fullPath.length()>1 && Character.isAlphabetic(fullPath.charAt(0)) && fullPath.charAt(1) == ':');
		}
		String realPath = fullPath;
		if( !abs) {
			if(currentDirectory != null ) {
				String cwd = currentDirectory.getAbsolutePath();
				char sep = currentDirectory.getFileSourceFactory().getSeperatorChar();
				if( cwd.endsWith(""+sep)) {
					realPath=cwd+fullPath;
				} else {
					realPath=cwd+sep+fullPath;
				}
			}
		}

		FileSource root=null;
		FileSource ret=null;

		for(FileSource tmp: listRoots()) {
			String rootPath =tmp.getAbsolutePath();
			if( realPath.startsWith(rootPath)) {
				ret=root = tmp;
				realPath = realPath.substring(rootPath.length());
				abs = realPath.startsWith("/");
				if( abs) {
					realPath=realPath.substring(1);
				}

				break;
			}
		}

		while(realPath.contains("..")) {
			int idx = realPath.indexOf("..");
			String left = realPath.substring(0,idx-1);
			String right = realPath.substring(idx+2);
			FileSource tmp = root.getChild(left);
			FileSource tmp2 = tmp.getParentFile();
			left = tmp2.getAbsolutePath();
			realPath = left+right;
		}
		
		if(root !=null &&  !realPath.isEmpty()) {

			ret = root.getChild(realPath);
		}

		if( ret == null ) {
			if(realPath.equals("/")) {
				ret = firstRoot;
			} else {
				FileSourceFactory f = firstRoot.getFileSourceFactory();
				ret = f.createFileSource(realPath);
			}
		}

		return ret;
	}

	@Override
	public String getTypeId() {
		return TYPE_ID;
	}

	@Override
	public boolean isConnected() {
		return true;
	}

	@Override
	protected boolean connectImpl() throws IOException {
		return true;
	}

	@Override
	public Component getEditPropertiesComponent() {
		return null;
	}

	@Override
	protected void disConnectImpl() throws IOException {
	}

	@Override
	public FileSourceFactory createThreadSafeCopy() {
		return this;
	}

	@Override
	public Properties getConnectProperties() {
		return new Properties();
	}

	@Override
	public char getPathSeperatorChar() {
		return ';';
	}

	@Override
	public char getSeperatorChar() {
		return '/';
	}

	@Override
	public FileSource createSymbolicLink(FileSource newFileLink, FileSource existingFile) throws IOException {
		throw new IOException("createSymbolicLink not implemented");
	}

	@Override
	public FileSource createLink(FileSource newFileLink, FileSource existingFile) throws IOException {
		throw new IOException("createLink not implemented");
	}

	@Override
	public void setConnectionProperties(URL url) {
	}

	@Override
	public void setConnectionProperties(Properties prop) {

	}

	@Override
	public String getTitle() {
		return TYPE_ID;
	}

	@Override
	public String getURL()  {
		throw new RuntimeException("getURL not implemented");
	}

	public boolean unmount(String mountPoint) {
		List<FileSource> tmp = new ArrayList<>();
		for(FileSource root : roots) {
			if(!root.getAbsolutePath().equals(mountPoint)) {
				tmp.add(root);
			}
		}
		if( tmp.size()!=roots.length) {
			roots = tmp.toArray(new FileSource[tmp.size()]);
			return true;
		}
		
		return false;
	}

}
