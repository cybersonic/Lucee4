/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/
package lucee.commons.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Map;
import java.util.Set;

import lucee.commons.collection.MapFactory;
import lucee.commons.io.FileUtil;
import lucee.commons.io.IOUtil;
import lucee.commons.io.SystemUtil;
import lucee.runtime.PageContextImpl;
import lucee.runtime.config.Config;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Array;
import lucee.runtime.type.util.ListUtil;


public final class ClassUtil {

	/**
	 * @param className
	 * @return
	 * @throws ClassException 
	 * @throws PageException
	 */
	public static Class toClass(String className) throws ClassException {
		return ClassUtil.loadClass(className);
	}
	
	private static Class checkPrimaryTypes(String className, Class defaultValue) {
		String lcClassName=className.toLowerCase();
		boolean isRef=false;
		
		if(lcClassName.startsWith("java.lang.")){
			lcClassName=lcClassName.substring(10);
			isRef=true;
		}

		if(lcClassName.equals("boolean") || className.equals("[Z"))	{ 
			if(isRef) return Boolean.class;
			return boolean.class; 
		}
		if(lcClassName.equals("byte") || className.equals("[B"))	{
			if(isRef) return Byte.class;
			return byte.class; 
		}
		if(lcClassName.equals("int") || className.equals("[I"))	{
			return int.class; 
		}
		if(lcClassName.equals("long") || className.equals("[J"))	{
			if(isRef) return Long.class;
			return long.class; 
		}
		if(lcClassName.equals("float") || className.equals("[F"))	{
			if(isRef) return Float.class;
			return float.class; 
		}
		if(lcClassName.equals("double") || className.equals("[D"))	{
			if(isRef) return Double.class;
			return double.class; 
		}
		if(lcClassName.equals("char") || className.equals("[C"))	{
			return char.class; 
		}
		if(lcClassName.equals("short") || className.equals("[S"))	{
			if(isRef) return Short.class;
			return short.class; 
		}
		
		if(lcClassName.equals("integer"))	return Integer.class; 
		if(lcClassName.equals("character"))	return Character.class; 
		if(lcClassName.equals("object"))	return Object.class; 
		if(lcClassName.equals("string"))	return String.class; 
		if(lcClassName.equals("null"))		return Object.class; 
		if(lcClassName.equals("numeric"))	return Double.class; 
		
		return defaultValue;
	}
	
	
	
	/**
	 * loads a class from a String classname
	 * @param className
	 * @param defaultValue 
	 * @return matching Class
	 */
	public static Class loadClass(String className, Class defaultValue) {
		return loadClass(null,className,defaultValue);
	}

	/**
	 * loads a class from a String classname
	 * @param className
	 * @return matching Class
	 * @throws ClassException 
	 */
	public static Class loadClass(String className) throws ClassException {
		Config config = ThreadLocalPageContext.getConfig();
		Class clazz = loadClass(config==null?null:config.getClassLoader(),className,null);
		if(clazz!=null) return clazz;
		throw new ClassException("cannot load class through its string name, because no definition for the class with the specified name ["+className+"] could be found");
	}
	
	/**
	 * loads a class from a specified Classloader with given classname
	 * @param className
	 * @param cl 
	 * @return matching Class
	 */
	public static Class loadClass(ClassLoader cl,String className, Class defaultValue) {
		className=className.trim();
		
		Class clazz = checkPrimaryTypes(className, null);
		if(clazz!=null) return clazz;
		
		if(cl==null){
			PageContextImpl pci = (PageContextImpl) ThreadLocalPageContext.get();
			if(pci!=null){
				try {
					cl=pci.getClassLoader();
				}
				catch (IOException e) {}
			}
			if(cl==null) {
				Config config = ThreadLocalPageContext.getConfig();
				if(config!=null)cl=config.getClassLoader();
			}
		}
		
		
		
		try {
			if(cl==null)return Class.forName(className.trim());
			return cl.loadClass(className.trim());
			
		}
		catch (ClassNotFoundException e) {
			try {
				return Class.forName(className, false, cl);
			} 
			catch (ClassNotFoundException e1) {
				// array in the format boolean[] or java.lang.String[]
				if(!StringUtil.isEmpty(className) && className.endsWith("[]")) {
					StringBuilder pureCN=new StringBuilder(className);
					int dimensions=0;
					do{
						pureCN.delete(pureCN.length()-2, pureCN.length());
						dimensions++;
					}
					while(pureCN.lastIndexOf("[]")==pureCN.length()-2);
					
					clazz = loadClass(cl,pureCN.toString(),null);
					if(clazz!=null) {
						for(int i=0;i<dimensions;i++)clazz=toArrayClass(clazz);
						return clazz;
					}
				}
				// array in the format [C or [Ljava.lang.String;
				else if(!StringUtil.isEmpty(className) && className.charAt(0)=='[') {
					StringBuilder pureCN=new StringBuilder(className);
					int dimensions=0;
					do{
						pureCN.delete(0, 1);
						dimensions++;
					}
					while(pureCN.charAt(0)=='[');
					
					clazz = loadClass(cl,pureCN.toString(),null);
					if(clazz!=null) {
						for(int i=0;i<dimensions;i++)clazz=toArrayClass(clazz);
						return clazz;
					}
				}
				// class in format Ljava.lang.String;
				else if(!StringUtil.isEmpty(className) && className.charAt(0)=='L' && className.endsWith(";")) {
					className=className.substring(1,className.length()-1).replace('/', '.');
					return loadClass(cl, className,defaultValue);
				}
				
				return defaultValue;
			}
		}
	}

	/**
	 * loads a class from a specified Classloader with given classname
	 * @param className
	 * @param cl 
	 * @return matching Class
	 * @throws ClassException 
	 */
	public static Class loadClass(ClassLoader cl,String className) throws ClassException {
		Class clazz = loadClass(cl,className,null);
		if(clazz!=null) return clazz;
		throw new ClassException("cannot load class through its string name, because no definition for the class with the specified name ["+className+"] could be found");
	}

	/**
	 * loads a class from a String classname
	 * @param clazz class to load
	 * @return matching Class
	 * @throws ClassException 
	 */
	public static Object loadInstance(Class clazz) throws ClassException{
		try {
			return clazz.newInstance();
		}
		catch (InstantiationException e) {
			throw new ClassException("the specified class object ["+clazz.getName()+"()] cannot be instantiated");
		}
		catch (IllegalAccessException e) {
			throw new ClassException("can't load class because the currently executing method does not have access to the definition of the specified class");
		}
	}

	public static Object loadInstance(String className) throws ClassException{
		return loadInstance(loadClass(className));
	}
	public static Object loadInstance(ClassLoader cl, String className) throws ClassException{
		return loadInstance(loadClass(cl,className));
	}
	
	/**
	 * loads a class from a String classname
	 * @param clazz class to load
	 * @return matching Class
	 */
	public static Object loadInstance(Class clazz, Object defaultValue){
		try {
			return clazz.newInstance();
		}
		catch (Throwable t) {
			return defaultValue;
		}
	}
	
	public static Object loadInstance(String className, Object deaultValue){
		Class clazz = loadClass(className,null);
		if(clazz==null) return deaultValue;
		return loadInstance(clazz,deaultValue);
	}
	
	public static Object loadInstance(ClassLoader cl, String className, Object deaultValue) {
		Class clazz = loadClass(cl,className,null);
		if(clazz==null) return deaultValue;
		return loadInstance(clazz,deaultValue);
	}
	
	/**
	 * loads a class from a String classname
	 * @param clazz class to load
	 * @param args 
	 * @return matching Class
	 * @throws ClassException 
	 * @throws ClassException 
	 * @throws InvocationTargetException 
	 */
	public static Object loadInstance(Class clazz, Object[] args) throws ClassException, InvocationTargetException {
		if(args==null || args.length==0) return loadInstance(clazz);
		
		Class[] cArgs=new Class[args.length];
		for(int i=0;i<args.length;i++) {
			cArgs[i]=args[i].getClass();
		}
		
		try {
			Constructor c = clazz.getConstructor(cArgs);
			return c.newInstance(args);
			
		}
		catch (SecurityException e) {
			throw new ClassException("there is a security violation (throwed by security manager)");
		}
		catch (NoSuchMethodException e) {
			
			StringBuilder sb=new StringBuilder(clazz.getName());
			char del='(';
			for(int i=0;i<cArgs.length;i++) {
				sb.append(del);
				sb.append(cArgs[i].getName());
				del=',';
			}
			sb.append(')');
			
			throw new ClassException("there is no constructor with this ["+sb+"] signature for the class ["+clazz.getName()+"]");
		}
		catch (IllegalArgumentException e) {
			throw new ClassException("has been passed an illegal or inappropriate argument");
		}
		catch (InstantiationException e) {
			throw new ClassException("the specified class object ["+clazz.getName()+"] cannot be instantiated because it is an interface or is an abstract class");
		}
		catch (IllegalAccessException e) {
			throw new ClassException("can't load class because the currently executing method does not have access to the definition of the specified class");
		}
	}

	public static Object loadInstance(String className, Object[] args) throws ClassException, InvocationTargetException{
		return loadInstance(loadClass(className),args);
	}
	
	public static Object loadInstance(ClassLoader cl, String className, Object[] args) throws ClassException, InvocationTargetException{
		return loadInstance(loadClass(cl,className),args);
	}
	
	/**
	 * loads a class from a String classname
	 * @param clazz class to load
	 * @param args 
	 * @return matching Class
	 */
	public static Object loadInstance(Class clazz, Object[] args, Object defaultValue) {
		if(args==null || args.length==0) return loadInstance(clazz,defaultValue);
		try {
			Class[] cArgs=new Class[args.length];
			for(int i=0;i<args.length;i++) {
				if(args[i]==null)cArgs[i]=Object.class;
				else cArgs[i]=args[i].getClass();
			}
			Constructor c = clazz.getConstructor(cArgs);
			return c.newInstance(args);
			
		}
		catch (Throwable t) {//print.printST(t);
			return defaultValue;
		}
		
	}
	
	public static Object loadInstance(String className, Object[] args, Object deaultValue){
		Class clazz = loadClass(className,null);
		if(clazz==null) return deaultValue;
		return loadInstance(clazz,args,deaultValue);
	}
	
	public static Object loadInstance(ClassLoader cl, String className, Object[] args, Object deaultValue) {
		Class clazz = loadClass(cl,className,null);
		if(clazz==null) return deaultValue;
		return loadInstance(clazz,args,deaultValue);
	}
	
	/**
	 * @return returns a string array of all pathes in classpath
	 */
	public static String[] getClassPath(Config config) {

        Map<String,String> pathes=MapFactory.<String,String>getConcurrentMap();
		String pathSeperator=System.getProperty("path.separator");
		if(pathSeperator==null)pathSeperator=";";
			
	// pathes from system properties
		String strPathes=System.getProperty("java.class.path");
		if(strPathes!=null) {
			Array arr=ListUtil.listToArrayRemoveEmpty(strPathes,pathSeperator);
			int len=arr.size();
			for(int i=1;i<=len;i++) {
				File file=FileUtil.toFile(Caster.toString(arr.get(i,""),"").trim());
				if(file.exists())
					try {
						pathes.put(file.getCanonicalPath(),"");
					} catch (IOException e) {}
			}
		}
		
		
	// pathes from url class Loader (dynamic loaded classes)
		getClassPathesFromLoader(new ClassUtil().getClass().getClassLoader(), pathes);
		getClassPathesFromLoader(config.getClassLoader(), pathes);
		
		Set set = pathes.keySet();
		return (String[]) set.toArray(new String[set.size()]);
	}
	
	/**
	 * get class pathes from all url ClassLoaders
	 * @param cl URL Class Loader
	 * @param pathes Hashmap with allpathes
	 */
	private static void getClassPathesFromLoader(ClassLoader cl, Map pathes) {
		if(cl instanceof URLClassLoader) 
			_getClassPathesFromLoader((URLClassLoader) cl, pathes);
	}
		
	
	private static void _getClassPathesFromLoader(URLClassLoader ucl, Map pathes) {
		getClassPathesFromLoader(ucl.getParent(), pathes);
		
		// get all pathes
		URL[] urls=ucl.getURLs();
		
		for(int i=0;i<urls.length;i++) {
			File file=FileUtil.toFile(urls[i].getPath());
			if(file.exists())
				try {
					pathes.put(file.getCanonicalPath(),"");
				} catch (IOException e) {}
		}
	}
	
	// CafeBabe (Java Magic Number)
	private static final int ICA=202;//CA 
    private static final int IFE=254;//FE
    private static final int IBA=186;//BA
    private static final int IBE=190;//BE
    
    // CF33 (Lucee Magic Number)
    private static final int ICF=207;//CF 
    private static final int I33=51;//33
    

	private static final byte BCA=(byte)ICA;//CA 
    private static final byte BFE=(byte)IFE;//FE
    private static final byte BBA=(byte)IBA;//BA
    private static final byte BBE=(byte)IBE;//BE
    
    private static final byte BCF=(byte)ICF;//CF 
    private static final byte B33=(byte)I33;//33
    
    
    /** 
     * check if given stream is a bytecode stream, if yes remove bytecode mark 
     * @param is 
     * @return is bytecode stream 
     * @throws IOException 
     */ 
    public static boolean isBytecode(InputStream is) throws IOException { 
            if(!is.markSupported()) 
                    throw new IOException("can only read input streams that support mark/reset"); 
            is.mark(-1); 
            //print(bytes);
            int first=is.read();
            int second=is.read();
             boolean rtn=(first==ICF && second==I33) || (first==ICA && second==IFE && is.read()==IBA && is.read()==IBE);
            
        is.reset(); 
        return rtn; 
    }
    

    public static boolean isBytecode(byte[] barr){ 
        if(barr.length<4) return false;
        return (barr[0]==BCF && barr[1]==B33) || (barr[0]==BCA && barr[1]==BFE && barr[2]==BBA && barr[3]==BBE); 
    }
    public static boolean isRawBytecode(byte[] barr){ 
        if(barr.length<4) return false;
        return (barr[0]==BCA && barr[1]==BFE && barr[2]==BBA && barr[3]==BBE); 
    }
    
    public static boolean hasCF33Prefix(byte[] barr) { 
        if(barr.length<4) return false;
        return (barr[0]==BCF && barr[1]==B33); 
    }
    
	public static byte[] removeCF33Prefix(byte[] barr) {
		if(!hasCF33Prefix(barr)) return barr;
    	
		byte[] dest = new byte[barr.length-10];
		System.arraycopy(barr, 10, dest, 0, 10);
		return dest;
	}

	public static String getName(Class clazz) {
		if(clazz.isArray()){
			return getName(clazz.getComponentType())+"[]";
		}
		
		return clazz.getName();
	}

	public static Method getMethodIgnoreCase(Class clazz, String methodName, Class[] args) throws ClassException {
		Method[] methods = clazz.getMethods();
		Method method;
		Class[] params;
		outer:for(int i=0;i<methods.length;i++){
			method=methods[i];
			if(method.getName().equalsIgnoreCase(methodName)){
				params = method.getParameterTypes();
				if(params.length==args.length){
					for(int y=0;y<params.length;y++){
						if(!params[y].equals(args[y])){
							continue outer;
						}
					}
					return method;
				}
			}
		}
		
		throw new ClassException("class "+clazz.getName()+" has no method with name "+methodName);
	}

	
	/**
	 * return all field names as String array
	 * @param clazz class to get field names from
	 * @return field names
	 */
	public static String[] getFieldNames(Class clazz) {
		Field[] fields = clazz.getFields();
		String[] names=new String[fields.length];
		for(int i=0;i<names.length;i++){
			names[i]=fields[i].getName();
		}
		return names;
	}

	public static byte[] toBytes(Class clazz) throws IOException {
		return IOUtil.toBytes(clazz.getClassLoader().getResourceAsStream(clazz.getName().replace('.','/')+".class"),true);
	}

	/**
	 * return a array class based on the given class (opposite from Class.getComponentType())
	 * @param clazz
	 * @return
	 */
	public static Class toArrayClass(Class clazz) {
		return java.lang.reflect.Array.newInstance(clazz, 0).getClass();
	}



	public static Class<?> toComponentType(Class<?> clazz) {
		Class<?> tmp;
		while(true){
			tmp=clazz.getComponentType();
			if(tmp==null) break;
			clazz=tmp;
		}
		return clazz;
	}


	/**
	 * returns the path to the directory or jar file that the class was loaded from
	 *
	 * @param clazz - the Class object to check, for a live object pass obj.getClass();
	 * @param defaultValue - a value to return in case the source could not be determined
	 * @return
	 */
	public static String getSourcePathForClass(Class clazz, String defaultValue) {

		try {

			String result = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
			result = URLDecoder.decode(result, Charset.UTF8);
			result = SystemUtil.fixWindowsPath(result);
			return result;
		}
		catch (Throwable t) {}

		return defaultValue;
	}
	
	/*
	public static String getLocation(Class clazz) {
		ClassLoader cl = clazz.getClassLoader();
		if(cl instanceof ResourceClassLoader) {
			StringBuilder sb=new StringBuilder();
			Resource[] sources = ((ResourceClassLoader)cl).getResources();
			if(sources!=null)for(int i=0;i<sources.length;i++){
				if(i>0)sb.append(';');
				sb.append(sources[i]);
			}
			return sb.toString();
		}
		else if(cl instanceof PhysicalClassLoader) {
			return ((PhysicalClassLoader)cl).getDirectory().getAbsolutePath();
		}
		else if(cl instanceof ArchiveClassLoader) {
			return ((ArchiveClassLoader)cl).getDirectory(); // not supporting info about source YET
		}
		
		try {
			URL loc = clazz.getProtectionDomain().getCodeSource().getLocation();
			if(loc!=null) return loc.toExternalForm();
			
		}
		catch (Throwable t) {}
		return "";
	}*/
	


	/**
	 * tries to load the class and returns the path that it was loaded from
	 *
	 * @param className - the name of the class to check
	 * @param defaultValue - a value to return in case the source could not be determined
	 * @return
	 */
	public static String getSourcePathForClass(String className, String defaultValue) {

		try {

			return  getSourcePathForClass(ClassUtil.loadClass(className), defaultValue);
		}
		catch (Throwable t) {}

		return defaultValue;
	}


}