package us.bringardner.shell;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import us.bringardner.io.filesource.FileSource;

public class FileSourceDynamicProxy implements InvocationHandler {

	private FileSource target;
	private FileSource parent;
	
	public FileSourceDynamicProxy(FileSource parent,FileSource target) {
		this.parent = parent;
		this.target = target;
	}
	
	FileSource getProxy(FileSource proxy,FileSource fs) {
		FileSource ret = (FileSource) Proxy.newProxyInstance(
				  RootFile.class.getClassLoader(), 
				  new Class[] { FileSource.class }, 
				  new FileSourceDynamicProxy(proxy,fs));

		return ret;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		    Object result = method.invoke(target, args);
		    String name = method.getName();
		    
		    if( name.equals("getAbsolutePath") || name.equals("getCanonicalPath") || name.equals("toString")) {
		    	RootFile root = findRoot(parent);
		    	String path = root.getAbsolutePath()+target.getAbsolutePath();
		    	if( path.startsWith("//")) {
		    		path = path.substring(1);
		    	}
		    	
		    	return path;
		    }  else if( name.equals("getParentFile")) {
		    	return parent;
		    } else if( name.equals("listFiles") && args == null) {
		    	FileSource kids [] = (FileSource[])result;
				FileSource [] ret = new FileSource[kids.length];
				for (int idx = 0; idx < ret.length; idx++) {
					ret[idx] = getProxy((FileSource) proxy,kids[idx]);
				}
		    	return ret;
		    } else if( name.equals("getChild")) {
		    	if( result != null ) {
		    		if (result instanceof FileSource) {
						FileSource target = (FileSource) result;
						return getProxy((FileSource)proxy, target);	
					}
		    		
		    		
		    	}
		    } else if( name.equals("isChildOfMine") && args.length==1) {
		    	String child = args[0].toString();
		    	String parent = proxy.toString();
		    	boolean ret = child.startsWith(parent);
		    	result = ret;
		    }
		    
		    
		    
		    
	        return result;		
	}

	private RootFile findRoot(FileSource file) throws IOException {
		if (file instanceof RootFile) {
			return (RootFile) file;
		}
		FileSource file2 = file.getParentFile();
		if( file2 == null ) {
			return null;
		}
		
		return findRoot(file2); 
		
	}

}
