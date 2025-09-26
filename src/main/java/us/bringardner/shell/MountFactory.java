package us.bringardner.shell;

import java.awt.Component;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

public class MountFactory extends FileSourceFactory {

	private static final long serialVersionUID = 1L;
	private FileSource [] roots;
	private VirtualFileSystem fileSystem;
	private FileSource currentDirectory;

	public MountFactory() throws IOException {
		FileSource localRoot = FileSourceFactory.getDefaultFactory().listRoots()[0];
		fileSystem = new VirtualFileSystem(localRoot);
		FileSource[] tmp = {fileSystem};
		roots = tmp;
		currentDirectory = roots[0];
	}

	public boolean mount(FileSourceFactory factory, String name) throws IOException {
		boolean ret = fileSystem.mount(factory.listRoots()[0], name);

		return ret;
	}

	public boolean unmount(String name) throws IOException {
		boolean ret = fileSystem.unmount(name);
		
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

		if(path.indexOf('~') >=0) {
			throw new IOException("Invalide file name path has ~");
		}

		if( path.equals("/")) {
			return fileSystem;
		}
		
		if( path.equals(".")) {
			return getCurrentDirectory();
		}
		
		
		String parts[] = path.split("["+getSeperatorChar()+"]");
		FileSource ret = fileSystem;
		if( path.charAt(0) != '/') {
			ret = getCurrentDirectory();
		}
		
		for(String part : parts) {
			if( part.equals(".")|| part.isEmpty()) {
				continue;
			}
			
			if( part.equals("..")) {
				ret = ret.getParentFile();
				if( ret == null) {
					throw new IOException("Invalide file name .. too far");
				}
			} else {
				ret = ret.getChild(part);
			}
		}
		
		

		return ret;
		
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

	public Map<String, FileSource> getMounts() {
		Map<String, FileSource> ret = new TreeMap<>();
		List<RootFile> mounts = fileSystem.getMounts();
		for(RootFile rf : mounts) {
			ret.put(rf.name, rf);
		}
		return ret;
	}

	public String getMountPoint(FileSourceFactory f) {
		int id = f.getSessionId();
		for(RootFile mount : fileSystem.getMounts()) {
			if( mount.target.getFileSourceFactory().getSessionId() == id) {
				return mount.name;
			}
		}
		
		return null;
	}

	public VirtualFileSystem getFileSystem() {
		return fileSystem;
	}

	public void setFileSystem(VirtualFileSystem fileSystem) {
		this.fileSystem = fileSystem;
	}

	
	
}
