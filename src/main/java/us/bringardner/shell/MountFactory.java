package us.bringardner.shell;

import java.awt.Component;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

public class MountFactory extends FileSourceFactory {

	private static final long serialVersionUID = 1L;
	FileSourceFactory defaultFactory;
	Map<String,FileSourceFactory> mounts = new TreeMap<>();
	FileSource [] roots = new FileSource[0];
	private FileSource currentDirectory;

	public MountFactory() throws IOException {
		defaultFactory = FileSourceFactory.getDefaultFactory();
		roots = defaultFactory.listRoots();	
		currentDirectory = roots[0];
	}

	public boolean mount(FileSourceFactory factory, String name) throws IOException {
		boolean ret = !mounts.containsKey(name);
		if( ret ) {

			FileSource [] kids = factory.listRoots();
			FileSource[] tmp = new FileSource[roots.length+kids.length];
			for (int idx = 0; idx < roots.length; idx++) {
				tmp[idx] = roots[idx];				
			}
			for (int idx = roots.length; idx < tmp.length; idx++) {
				tmp[idx] = new RootFile(name, kids[idx-roots.length]);
			}
			roots = tmp;
			mounts.put(name, factory);
		}

		return ret;
	}

	public boolean unmount(String name) throws IOException {
		boolean ret = mounts.containsKey(name);
		if( ret ) {
			FileSourceFactory mnt = mounts.remove(name);
			if( mnt != null ) {
				int pos = -1;
				for (int idx = 0; idx < roots.length; idx++) {
					if( roots[idx].getName().equals(name)) {
						pos = idx;
						break;
					}
				}
				if( pos < 0 ) {
					ret = false;
				} else {

					FileSource[] tmp = new FileSource[roots.length-1];
					for (int idx = 0,pos1=0; idx < roots.length; idx++) {
						if( idx != pos) {
							tmp[pos1++] = roots[idx];
						}
					}
					mnt.disConnect();
					roots = tmp;
				}
			}
		}

		return ret;
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
	public FileSource createFileSource(String path) throws IOException {
		if(path == null || (path=path.trim()).isEmpty()) {
			throw new IOException("Invalide file name");
		}

		if( path.equals("/")) {
			return roots[0];
		}

		if( path.equals(".")) {
			return getCurrentDirectory();
		}
		if( path.equals("..")) {
			return getCurrentDirectory().getParentFile();
		}
		
		FileSource parent = null;
		if( path.startsWith("..")) {
			parent = getCurrentDirectory().getParentFile();
		}
		
		if( path.startsWith("~") ) {
			path = getCurrentDirectory().getAbsolutePath()+path.substring(1);
		}
		
		//  the local file system should have "" as name
		String mountPoint = "";
		String childPath = path;

		char sep = getPathSeperatorChar();

		if( path.charAt(0) != sep) {
			parent =  getCurrentDirectory();
		} else {
			int idx1 = path.indexOf(sep, 1);
			if( idx1 > 0 ) {
				mountPoint = path.substring(0,idx1);
				childPath = path.substring(idx1+1);
			}
			for (int idx = 0; idx < roots.length; idx++) {
				FileSource rf = roots[idx];
				String  rootName = rf.getAbsolutePath();
				if( rootName.equals(mountPoint)) {
					parent = rf;	
					break;
				} else if( rootName.equals(childPath)) {
					return rf;						
				}
			}
			
		}

		if( parent == null ) {
			return defaultFactory.createFileSource(path);
		} else {
			return parent.getChild(childPath);
		}
		
	}

	public FileSource createFileSource1(String fullPath) throws IOException {
		int idx = fullPath.indexOf(getSeperatorChar(), 1);
		if( idx <=0 ) {
			return FileSourceFactory.getDefaultFactory().createFileSource(fullPath);
		}
		String name = fullPath.substring(1, idx);
		FileSourceFactory factory = mounts.get(name);
		if( factory == null ) {
			return FileSourceFactory.getDefaultFactory().createFileSource(fullPath);
		}

		return null;
	}

	@Override
	public String getTypeId() {
		return "mountedFileSystem";
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
		return null;
	}

	@Override
	public Properties getConnectProperties() {
		return new Properties();
	}

	@Override
	public char getPathSeperatorChar() {
		return '/';
	}

	@Override
	public char getSeperatorChar() {
		return '/';
	}

	@Override
	public void setConnectionProperties(URL url) {
	}

	@Override
	public void setConnectionProperties(Properties prop) {
	}

	@Override
	public String getTitle() {
		return "mounts";
	}

	@Override
	public String getURL() {
		return null;
	}

	@Override
	public FileSource createSymbolicLink(FileSource newFileLink, FileSource existingFile) throws IOException {
		FileSourceFactory f1 = newFileLink.getFileSourceFactory();
		FileSourceFactory f2 = newFileLink.getFileSourceFactory();
		if( !f1.getTypeId().equals(f2.getTypeId())) {
			throw new IOException("Factories do not match "+f1.getTypeId()+" : "+f2.getTypeId());
		}
		return f1.createSymbolicLink(newFileLink, existingFile);
	}

	@Override
	public FileSource createLink(FileSource newFileLink, FileSource existingFile) throws IOException {
		FileSourceFactory f1 = newFileLink.getFileSourceFactory();
		FileSourceFactory f2 = newFileLink.getFileSourceFactory();
		if( !f1.getTypeId().equals(f2.getTypeId())) {
			throw new IOException("Factories do not match "+f1.getTypeId()+" : "+f2.getTypeId());
		}
		return f1.createLink(newFileLink, existingFile);
	}

	public Map<String, FileSourceFactory> getMounts() {
		return mounts;
	}

	public void setMounts(Map<String, FileSourceFactory> mounts) {
		this.mounts = mounts;
	}

	public String getMountPoint(FileSourceFactory f) {
		int id = f.getSessionId();
		for(Entry<String, FileSourceFactory> m : mounts.entrySet()) {
			if(m.getValue().getSessionId()==id) {
				return m.getKey();
			}
		}
		if( id == defaultFactory.getSessionId()) {
			return "/";
		}
		return null;
	}

}
