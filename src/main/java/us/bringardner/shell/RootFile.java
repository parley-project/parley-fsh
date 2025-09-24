/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.01.05-V000.01.15-V000.01.00-V000.00.01-V000.00.00-
 */

package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ProgressMonitor;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceFilter;
import us.bringardner.io.filesource.ISeekableInputStream;

/**
 * A FileSource act as a root when a factory has more than one root
 */
public class RootFile implements FileSource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	String name = "mount";
	FileSource target;
	private FileSource parent;
	
	public RootFile (String name, FileSource dir,FileSource parent) throws IOException {
		target = dir;
		this.parent = parent;
		this.name = name;
	}
	
	@Override
	public String toString() {
		return getAbsolutePath();
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public long length() {
		return 0;
	}

	@Override
	public boolean isDirectory() {
		return true;
	}

	@Override
	public long lastModified() {
		return 0;
	}

	FileSource getProxy(FileSource fs) {
		FileSource ret = (FileSource) Proxy.newProxyInstance(
				  getClass().getClassLoader(), 
				  new Class[] { FileSource.class }, 
				  new FileSourceDynamicProxy(this,fs)
		);

		return ret;
	}
	
	@Override
	public FileSource[] listFiles() throws IOException {
		FileSource kids [] = target.listFiles();
		FileSource [] ret = new FileSource[kids.length];
		for (int idx = 0; idx < ret.length; idx++) {
			ret[idx] = getProxy(kids[idx]);
		}
		
		return ret;
	}


	
	@Override
	public String getAbsolutePath() {
		
		return "/"+name;
	}

	

	@Override
	public InputStream getInputStream() throws IOException {
		throw new IllegalStateException("Can't get a stream from a root");	
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		throw new IllegalStateException("Can't get a stream from a root");
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public boolean mkdirs() throws IOException {
		return true;
	}

	@Override
	public FileSource getParentFile() throws IOException {
		return parent;
	}

	@Override
	public boolean delete() throws IOException {
		return false;
	}

	@Override
	public void refresh() throws IOException {
		
	}

	
	public String getTitle() {
		return getName();
	}

	
	public FileSource getLinkedTo() throws IOException {
		return target.getLinkedTo();
	}

	@Override
	public int compareTo(Object o) {
		return toString().compareTo(o.toString());
	}

	@Override
	public long getCreateDate() throws IOException {
		return 0;
	}

	@Override
	public String getContentType() {
		return null;
	}

	@Override
	public boolean canRead()  {
		return true;
	}

	@Override
	public boolean canWrite()  {
		return false;
	}

	@Override
	public boolean createNewFile() throws IOException {
		return false;
	}

	@Override
	public FileSource getChild(String path) throws IOException {
		if( path.startsWith("/")) {
			return target.getFileSourceFactory().createFileSource(path);
		}
		FileSource ret = target.getChild(path);
		
		return getProxy(ret);
	}

	@Override
	public FileSourceFactory getFileSourceFactory() {
		return target.getFileSourceFactory();
	}

	@Override
	public String getCanonicalPath() throws IOException {
		return target.getCanonicalPath();
	}

	@Override
	public String getParent() {		
		return parent == null?null:parent.getName();
	}

	@Override
	public boolean isChildOfMine(FileSource child) throws IOException {
		boolean ret = child.getAbsolutePath().startsWith(getAbsolutePath());
		return ret;
	}

	@Override
	public boolean isFile() throws IOException {
		return target.isFile();
	}

	@Override
	public String[] list() throws IOException {
		FileSource[] list = listFiles();
		String ret [] = new String[list.length];
		for (int idx=0; idx < list.length; idx++) {
			ret[idx] = list[idx].getName();
		}
		return ret;
	}

	@Override
	public String[] list(FileSourceFilter filter) throws IOException {
		FileSource[] list = listFiles(filter);
		String ret [] = new String[list.length];
		for (int idx=0; idx < list.length; idx++) {
			ret[idx] = list[idx].getName();
		}
		return ret;
	}

	@Override
	public FileSource[] listFiles(FileSourceFilter filter) throws IOException {
		List<FileSource> list = new ArrayList<FileSource>();
		for (FileSource f : listFiles()) {
			if( filter.accept(f)) {
				list.add(f);
			}
		}
		
		return list.toArray(new FileSource[list.size()]);
	}

	@Override
	public boolean mkdir() throws IOException {
		return false;
	}

	@Override
	public boolean renameTo(FileSource dest) throws IOException {
		return false;
	}

	
	@Override
	public boolean setReadOnly() throws IOException {
		return true;
	}

	@Override
	public void dereferenceChilderen() {
		
	}

	@Override
	public OutputStream getOutputStream(boolean append) throws IOException {
		throw new IOException();
	}

	@Override
	public URL toURL() throws MalformedURLException {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public boolean isVersionSupported() throws IOException {
		return false;
	}

	@Override
	public long getVersion() throws IOException {
		return 0;
	}

	@Override
	public long getVersionDate() throws IOException {
		return 0;
	}

	@Override
	public long getMaxVersion() {
		return 0;
	}

	@Override
	public InputStream getInputStream(long bytesRec) throws IOException {
		throw new IOException();
	}

	
	@Override
	public FileSource[] listFiles(ProgressMonitor progress) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public boolean isHidden() {
		return false;
	}

	@Override
	public ISeekableInputStream getSeekableInputStream() throws IOException {
		throw new IOException("ISeekableInputStream not imploemented");
	}

	
	@Override
	public GroupPrincipal getGroup() throws IOException {		
		return target.getGroup();
	}

	@Override
	public UserPrincipal getOwner() throws IOException {
		return target.getOwner();
	}

	
	@Override
	public long lastAccessTime() throws IOException {
		return target.lastAccessTime();
	}

	@Override
	public long creationTime() throws IOException {
		return target.creationTime();
	}

	@Override
	public boolean canOwnerRead() throws IOException {
		return target.canOwnerRead();
	}

	@Override
	public boolean canOwnerWrite() throws IOException {
		return target.canOwnerWrite();
	}

	@Override
	public boolean canOwnerExecute() throws IOException {
		return target.canOwnerExecute();
	}

	@Override
	public boolean canGroupRead() throws IOException {
		return true;
	}

	@Override
	public boolean canGroupWrite() throws IOException {
		return false;
	}

	@Override
	public boolean canGroupExecute() throws IOException {
		return false;
	}

	@Override
	public boolean canOtherRead() throws IOException {
		return true;
	}

	@Override
	public boolean canOtherWrite() throws IOException {
		return false;
	}

	@Override
	public boolean canOtherExecute() throws IOException {
		return false;
	}

	@Override
	public boolean setLastModifiedTime(long time) throws IOException {
		return false;
	}

	@Override
	public boolean setLastAccessTime(long time) throws IOException {
		return false;
	}

	@Override
	public boolean setCreateTime(long time) throws IOException {
		return false;
	}

	@Override
	public boolean setExecutable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setReadable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setWritable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setExecutable(boolean b, boolean ownerOnly) throws IOException {
		return false;
	}

	@Override
	public boolean setReadable(boolean b, boolean ownerOnly) throws IOException {
		return false;
	}

	@Override
	public boolean setWritable(boolean b, boolean ownerOnly) throws IOException {
		return false;
	}

	@Override
	public boolean setOwnerExecutable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setOwnerReadable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setOwnerWritable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setGroupExecutable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setGroupReadable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setGroupWritable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setOtherExecutable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setOtherReadable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setOtherWritable(boolean b) throws IOException {
		return false;
	}

	@Override
	public boolean setVersionDate(long time) throws IOException {
		return false;
	}

	@Override
	public boolean setVersion(long version, boolean saveChange) throws IOException {
		return false;
	}

	@Override
	public boolean setGroup(GroupPrincipal group) throws IOException {
		return false;
	}

	@Override
	public boolean setOwner(UserPrincipal owner) throws IOException {
		return false;
	}
	
}
