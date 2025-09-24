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
 * ~version~V000.01.31-V000.01.05-V000.01.15-V000.01.00-V000.00.01-V000.00.00-
 */

package us.bringardner.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ProgressMonitor;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.FileSourceFilter;
import us.bringardner.io.filesource.ISeekableInputStream;

public class FileSourceFileSystem implements FileSource {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private FileSource primary; 
	private long lastUpdate = 0;
	private List<FileSource> kids;
	private List<RootFile> mounts = new ArrayList<>();

	public FileSourceFileSystem (FileSource primary) throws IOException {
		if( !primary.isDirectory()) {
			throw new IOException(primary+" is NOT a directory");
		}
		this.primary = primary;
	}

	public boolean mount2(FileSource dir, String mountPoint ) throws IOException {
		boolean ret = false;
		if( mountPoint == null || mountPoint.isEmpty() || mountPoint.charAt(0) != '/') {
			throw new IOException("Invalid mount point = "+mountPoint);
		}
		String [] parts = mountPoint.split("[/]");
		FileSource parent = primary;
		for (int idx = 1; idx < parts.length-1; idx++) {
			String part = parts[idx].trim();
			System.out.println(""+idx+") "+part);
			if( !part.isEmpty()) {
				FileSource tmp = parent.getChild(part);
				if( !tmp.isDirectory()) {
					throw new IOException("Invalid mount point : "+part+" is not a directory.");
				}
				parent = tmp;
			}
		}
		String name = parts[parts.length-1];
		FileSource tmp = parent.getChild(name);

		System.out.println("tmp="+tmp);
		return ret;
	}
	
	public boolean mount(FileSource dir, String name ) throws IOException {
		FileSource tmp[] = primary.listFiles();		
		for (int idx = 0; idx < tmp.length; idx++) {
			if(tmp[idx].getName().equals(name)) {
				return false;
			}
		}

		for (int idx = 0; idx < mounts.size(); idx++) {
			if( mounts.get(idx).name.equals(name)) {
				return false;
			}
		}

		mounts.add(new RootFile(name, dir,this));
		kids = null;
		return  true;
	}

	public boolean unmount(String name) throws IOException {
		boolean ret = false;
		int pos = -1;
		for(int idx=0,sz=mounts.size(); idx < sz; idx++ ) {
			RootFile rf = mounts.get(idx);
			if( rf.name.equals(name)) {
				pos = idx;
				break;
			}
		}
		
		if( pos >=0 ) {
			ret = mounts.remove(pos) !=null;
		}
		
		return ret;
	}

	@Override
	public String toString() {
		return getAbsolutePath();
	}


	@Override
	public long length() throws IOException {
		return primary.length();
	}

	@Override
	public boolean isDirectory() throws IOException {
		return primary.isDirectory();
	}

	@Override
	public long lastModified() throws IOException {
		return primary.lastModified();
	}

	@Override
	public FileSource[] listFiles() throws IOException {
		if( kids == null || (System.currentTimeMillis()-lastUpdate)> 2000) {
			synchronized (this) {
				if( kids == null || (System.currentTimeMillis()-lastUpdate)> 2000) {
					FileSource [] list = primary.listFiles();
					List<FileSource>  all = new ArrayList<>();
					for (int idx = 0; idx < list.length; idx++) {
						all.add( list[idx]);
					}

					all.addAll(mounts);
					Collections.sort(all);
					kids = all;
				}
			}
		}

		return kids.toArray(new FileSource[kids.size()]);
	}



	@Override
	public String getAbsolutePath() {		
		return primary.getAbsolutePath();
	}



	@Override
	public InputStream getInputStream() throws IOException {
		throw new IOException("Can't get a stream from a root");	
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		throw new IOException("Can't get a stream from a root");
	}

	@Override
	public boolean exists() throws IOException {
		return primary.exists();
	}

	@Override
	public boolean mkdirs() throws IOException {
		return primary.mkdirs();
	}

	@Override
	public FileSource getParentFile() throws IOException {
		return primary.getParentFile();
	}

	@Override
	public boolean delete() throws IOException {
		return primary.delete();
	}

	@Override
	public void refresh() throws IOException {
		kids = null;
	}


	public String getTitle() {
		return getName();
	}


	public FileSource getLinkedTo() throws IOException {
		return primary.getLinkedTo();
	}

	@Override
	public int compareTo(Object o) {
		return primary.compareTo(o);
	}

	@Override
	public long getCreateDate() throws IOException {
		return 0;
	}

	@Override
	public String getContentType() {
		return primary.getContentType();
	}

	@Override
	public boolean canRead() throws IOException  {
		return primary.canRead();
	}

	@Override
	public boolean canWrite() throws IOException  {
		return primary.canWrite();
	}

	@Override
	public boolean createNewFile() throws IOException {
		return primary.createNewFile();
	}

	@Override
	public FileSource getChild(String name) throws IOException {
		FileSource ret = null;
		
		for(RootFile rf : mounts) {
			if(rf.name.equals(name)) {
				return rf;
			}
		}
	
		String[] parts = name.split("["+primary.getFileSourceFactory().getSeperatorChar()+"]");
		FileSource parent = primary;
		
		
		for(String part : parts) {
			for(RootFile rf : mounts) {
				if(rf.name.equals(part)) {
					parent = rf;
					break;
				}
			}
		}
		
		ret =  parent.getChild(parts[parts.length-1]);
		
		return ret;
	}

	@Override
	public FileSourceFactory getFileSourceFactory() {
		return primary.getFileSourceFactory();
	}

	@Override
	public String getCanonicalPath() throws IOException {
		return getAbsolutePath();
	}

	@Override
	public String getParent() {
		return primary.getParent();
	}

	@Override
	public boolean isChildOfMine(FileSource child) throws IOException {
		boolean ret = false;
		FileSource lastParent =null, tmp= child.getParentFile();
		
		while(tmp !=null) {
			lastParent = tmp;
			tmp = tmp.getParentFile();
		}
		if( lastParent != null ) {
			ret = lastParent.getAbsolutePath().equals(primary.getAbsolutePath());
		}
		
		return ret;
	}

	@Override
	public boolean isFile() throws IOException {
		return primary.isFile();
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
		return primary.mkdir();
	}

	@Override
	public boolean renameTo(FileSource dest) throws IOException {
		return primary.renameTo(dest);
	}


	@Override
	public boolean setReadOnly() throws IOException {
		return primary.setReadOnly();
	}

	@Override
	public void dereferenceChilderen() {
		kids = null;
	}

	@Override
	public OutputStream getOutputStream(boolean append) throws IOException {
		return primary.getOutputStream();
	}

	@Override
	public URL toURL() throws MalformedURLException {
		return primary.toURL();
	}

	@Override
	public boolean isVersionSupported() throws IOException {
		return primary.isVersionSupported();
	}

	@Override
	public long getVersion() throws IOException {
		return primary.getVersion();
	}

	@Override
	public long getVersionDate() throws IOException {
		return primary.getVersionDate();
	}

	@Override
	public long getMaxVersion() throws IOException {
		return primary.getMaxVersion();
	}

	@Override
	public InputStream getInputStream(long bytesRec) throws IOException {
		return primary.getInputStream();
	}


	@Override
	public FileSource[] listFiles(ProgressMonitor progress) {		
		throw new RuntimeException("Not implemented");
	}

	@Override
	public boolean isHidden() throws IOException {
		return primary.isHidden();
	}

	@Override
	public ISeekableInputStream getSeekableInputStream() throws IOException {
		return primary.getSeekableInputStream();
	}


	@Override
	public GroupPrincipal getGroup() throws IOException {		
		return primary.getGroup();
	}

	@Override
	public UserPrincipal getOwner() throws IOException {
		return primary.getOwner();
	}


	@Override
	public long lastAccessTime() throws IOException {
		return primary.lastAccessTime();
	}

	@Override
	public long creationTime() throws IOException {
		return primary.creationTime();
	}

	@Override
	public boolean canOwnerRead() throws IOException {
		return primary.canOtherRead();
	}

	@Override
	public boolean canOwnerWrite() throws IOException {
		return primary.canOwnerWrite();
	}

	@Override
	public boolean canOwnerExecute() throws IOException {
		return primary.canOwnerExecute();
	}

	@Override
	public boolean canGroupRead() throws IOException {
		return primary.canGroupRead();
	}

	@Override
	public boolean canGroupWrite() throws IOException {
		return primary.canGroupWrite();
	}

	@Override
	public boolean canGroupExecute() throws IOException {
		return primary.canGroupExecute();
	}

	@Override
	public boolean canOtherRead() throws IOException {
		return primary.canOtherRead();
	}

	@Override
	public boolean canOtherWrite() throws IOException {
		return primary.canOtherWrite();
	}

	@Override
	public boolean canOtherExecute() throws IOException {
		return primary.canOtherExecute();
	}

	@Override
	public boolean setLastModifiedTime(long time) throws IOException {
		return primary.setLastModifiedTime(time);
	}

	@Override
	public boolean setLastAccessTime(long time) throws IOException {
		return primary.setLastAccessTime(time);
	}

	@Override
	public boolean setCreateTime(long time) throws IOException {
		return primary.setCreateTime(time);
	}

	@Override
	public boolean setExecutable(boolean b) throws IOException {
		return primary.setExecutable(b);
	}

	@Override
	public boolean setReadable(boolean b) throws IOException {
		return primary.setReadable(b);
	}

	@Override
	public boolean setWritable(boolean b) throws IOException {
		return primary.setWritable(b);
	}

	@Override
	public boolean setExecutable(boolean b, boolean ownerOnly) throws IOException {
		return primary.setExecutable(b,ownerOnly);
	}

	@Override
	public boolean setReadable(boolean b, boolean ownerOnly) throws IOException {
		return primary.setReadable(b, ownerOnly);
	}

	@Override
	public boolean setWritable(boolean b, boolean ownerOnly) throws IOException {
		return primary.setWritable(b, ownerOnly);
	}

	@Override
	public boolean setOwnerExecutable(boolean b) throws IOException {
		return primary.setOtherExecutable(b);
	}

	@Override
	public boolean setOwnerReadable(boolean b) throws IOException {
		return primary.setOtherReadable(b);
	}

	@Override
	public boolean setOwnerWritable(boolean b) throws IOException {
		return primary.setOtherWritable(b);
	}

	@Override
	public boolean setGroupExecutable(boolean b) throws IOException {
		return primary.setGroupExecutable(b); 
	}

	@Override
	public boolean setGroupReadable(boolean b) throws IOException {
		return primary.setGroupReadable(b);
	}

	@Override
	public boolean setGroupWritable(boolean b) throws IOException {
		return primary.setGroupWritable(b);
	}

	@Override
	public boolean setOtherExecutable(boolean b) throws IOException {
		return primary.setOtherExecutable(b);
	}

	@Override
	public boolean setOtherReadable(boolean b) throws IOException {
		return primary.setOtherReadable(b);
	}

	@Override
	public boolean setOtherWritable(boolean b) throws IOException {
		return primary.setOtherWritable(b);
	}

	@Override
	public boolean setVersionDate(long time) throws IOException {
		return primary.setVersionDate(time);
	}

	@Override
	public boolean setVersion(long version, boolean saveChange) throws IOException {
		return primary.setVersion(version, saveChange);
	}

	@Override
	public boolean setGroup(GroupPrincipal group) throws IOException {
		return primary.setGroup(group);
	}

	@Override
	public boolean setOwner(UserPrincipal owner) throws IOException {
		return primary.setOwner(owner);
	}

	@Override
	public String getName() {		
		return primary.getName();
	}

	public List<RootFile> getMounts() {
		return mounts;
	}

}
