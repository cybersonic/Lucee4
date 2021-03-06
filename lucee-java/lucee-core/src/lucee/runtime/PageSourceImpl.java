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
package lucee.runtime;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import lucee.commons.io.CharsetUtil;
import lucee.commons.io.IOUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.commons.lang.SizeOf;
import lucee.commons.lang.StringUtil;
import lucee.commons.lang.types.RefBoolean;
import lucee.commons.lang.types.RefBooleanImpl;
import lucee.runtime.config.ConfigImpl;
import lucee.runtime.config.ConfigWeb;
import lucee.runtime.config.ConfigWebImpl;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.engine.ThreadLocalPageSource;
import lucee.runtime.exp.ExpressionException;
import lucee.runtime.exp.MissingIncludeException;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.TemplateException;
import lucee.runtime.functions.system.GetDirectoryFromPath;
import lucee.runtime.op.Caster;
import lucee.runtime.type.Sizeable;
import lucee.runtime.type.util.ArrayUtil;
import lucee.runtime.type.util.ListUtil;

/**
 * represent a cfml file on the runtime system
 */
public final class PageSourceImpl implements PageSource, Sizeable {

	private static final long serialVersionUID = -7661676586215092539L;
	//public static final byte LOAD_NONE=1;
    public static final byte LOAD_ARCHIVE=2;
    public static final byte LOAD_PHYSICAL=3;
    
    //private byte load=LOAD_NONE;

	private final MappingImpl mapping;
    private final String relPath;
    
    private boolean isOutSide;
    
    private String className;
    private String packageName;
    private String javaName;

    private Resource physcalSource;
    private Resource archiveSource;
    private String fileName;
    private String compName;
    private Page page;
	private long lastAccess;	
	private int accessCount=0;
	private boolean flush=false;
    //private boolean recompileAlways;
    //private boolean recompileAfterStartUp;
    
    private PageSourceImpl() {
    	mapping=null;
        relPath=null;
    }
    
    
    /**
	 * constructor of the class
     * @param mapping
     * @param relPath
	 */
	PageSourceImpl(MappingImpl mapping,String relPath) {
		this.mapping=mapping;
        relPath=relPath.replace('\\','/');
        if(relPath.indexOf("//")!=-1) {
        	//print.ds(relPath);
        	relPath=StringUtil.replace(relPath, "//", "/");
        }
        
        
		if(relPath.indexOf('/')!=0) {
		    if(relPath.startsWith("../")) {
				isOutSide=true;
			}
			else if(relPath.startsWith("./")) {
				relPath=relPath.substring(1);
			}
			else {
				relPath="/"+relPath;
			}
		}
		this.relPath=relPath;
	    
	}
	
	
	
	/**
	 * private constructor of the class
	 * @param mapping
	 * @param relPath
	 * @param isOutSide
	 */
    PageSourceImpl(MappingImpl mapping, String relPath, boolean isOutSide) {
    	//recompileAlways=mapping.getConfig().getCompileType()==Config.RECOMPILE_ALWAYS;
        //recompileAfterStartUp=mapping.getConfig().getCompileType()==Config.RECOMPILE_AFTER_STARTUP || recompileAlways;
        this.mapping=mapping;
	    this.isOutSide=isOutSide;
	    if(relPath.indexOf("//")!=-1) {
        	//print.ds(relPath);
        	relPath=StringUtil.replace(relPath, "//", "/");
        }
		this.relPath=relPath;
		
	}
	
	/**
	 * return page when already loaded, otherwise null
	 * @param pc
	 * @param config
	 * @return
	 * @throws PageException
	 */
	public Page getPage() {
		return page;
	}
	
	public PageSource getParent(){
		if(relPath.equals("/")) return null;
		if(StringUtil.endsWith(relPath, '/'))
			return new PageSourceImpl(mapping, GetDirectoryFromPath.invoke(relPath.substring(0, relPath.length()-1)));
		return new PageSourceImpl(mapping, GetDirectoryFromPath.invoke(relPath));
	}

	
	@Override
	public Page loadPage(ConfigWeb config) throws PageException {
		return loadPage(ThreadLocalPageContext.get());
	}

	@Override
	public Page loadPage(ConfigWeb config, Page defaultValue) throws PageException {
		return loadPage(ThreadLocalPageContext.get(), defaultValue);
	}
	

	public Page loadPage(PageContext pc, boolean forceReload) throws PageException {
		if(forceReload) page=null;
		return loadPage(pc);
	}
	
	public Page loadPage(PageContext pc) throws PageException {
		Page page=this.page;
		if(mapping.isPhysicalFirst()) {
			page=loadPhysical(pc,page);
			if(page==null) page=loadArchive(page); 
	        if(page!=null) return page;
	    }
	    else {
	        page=loadArchive(page);
	        if(page==null)page=loadPhysical(pc,page);
	        if(page!=null) return page;
	    }
		throw new MissingIncludeException(this);
	    
	}
	
	@Override
	public Page loadPage(PageContext pc, Page defaultValue) throws PageException {
		Page page=this.page;
		if(mapping.isPhysicalFirst()) {
	        page=loadPhysical(pc,page);
	        if(page==null) page=loadArchive(page); 
	        if(page!=null) return page;
	    }
	    else {
	    	page=loadArchive(page);
	        if(page==null)page=loadPhysical(pc,page);
	        if(page!=null) return page;
	    }
	    return defaultValue;
	}
	
    private Page loadArchive(Page page) {
    	if(!mapping.hasArchive()) return null;
		if(page!=null && page.getLoadType()==LOAD_ARCHIVE) return page;
        
        try {
            synchronized(this) {
                Class clazz=mapping.getClassLoaderForArchive().loadClass(getClazz());
                this.page=page=newInstance(clazz);
                page.setPageSource(this);
                //page.setTimeCreated(System.currentTimeMillis());
                page.setLoadType(LOAD_ARCHIVE);
    			////load=LOAD_ARCHIVE;
    			return page;
            }
        } 
        catch (Exception e) {
        	return null;
        }
    }
    

    private Page loadPhysical(PageContext pc,Page page) throws PageException {
    	if(!mapping.hasPhysical()) return null;
    	
    	ConfigWeb config=pc.getConfig();
    	PageContextImpl pci=(PageContextImpl) pc;
    	if((mapping.getInspectTemplate()==ConfigImpl.INSPECT_NEVER || pci.isTrusted(page)) && isLoad(LOAD_PHYSICAL)) return page;
    	Resource srcFile = getPhyscalFile();
    	
		long srcLastModified = srcFile.lastModified();
        if(srcLastModified==0L) return null;
    	
		// Page exists    
			if(page!=null) {
			//if(page!=null && !recompileAlways) {
				// java file is newer !mapping.isTrusted() && 
				if(srcLastModified!=page.getSourceLastModified()) {
					this.page=page=compile(config,mapping.getClassRootDirectory(),Boolean.TRUE);
                	page.setPageSource(this);
					page.setLoadType(LOAD_PHYSICAL);
				}
		    	
			}
		// page doesn't exist
			else {
                ///synchronized(this) {
                    Resource classRootDir=mapping.getClassRootDirectory();
                    Resource classFile=classRootDir.getRealResource(getJavaName()+".class");
                    boolean isNew=false;
                    // new class
                    if(flush || !classFile.exists()) {
                    //if(!classFile.exists() || recompileAfterStartUp) {
                    	this.page=page= compile(config,classRootDir,Boolean.FALSE);
                    	flush=false;
                        isNew=true;
                    }
                    // load page
                    else {
                    	try {
							this.page=page=newInstance(mapping.touchPCLCollection().getClass(this));
						} catch (Throwable t) {t.printStackTrace();
							this.page=page=null;
						}
                    	if(page==null) this.page=page=compile(config,classRootDir,Boolean.TRUE);
                              
                    }
                    
                    // check if there is a newwer version
                    if(!isNew && srcLastModified!=page.getSourceLastModified()) {
                    	isNew=true;
                    	this.page=page=compile(config,classRootDir,null);
    				}
                    
                    // check version
                    if(!isNew && page.getVersion()!=Info.getFullVersionInfo()) {
                    	isNew=true;
                    	this.page=page=compile(config,classRootDir,null);
                    }
                    
                    page.setPageSource(this);
    				page.setLoadType(LOAD_PHYSICAL);

			}
			pci.setPageUsed(page);
			return page;
    }

    private boolean isLoad(byte load) {
		return page!=null && load==page.getLoadType();
	}
    
    public void flush() {
		page=null;
		flush=true;
	}
    

	private synchronized Page compile(ConfigWeb config,Resource classRootDir, Boolean resetCL) throws PageException {
		try {
			return _compile(config, classRootDir, resetCL);
        }
			catch(RuntimeException re) {re.printStackTrace();
	    	String msg=StringUtil.emptyIfNull(re.getMessage());
	    	if(StringUtil.indexOfIgnoreCase(msg, "Method code too large!")!=-1) {
	    		throw new TemplateException("There is too much code inside the template ["+getDisplayPath()+"], Lucee was not able to break it into pieces, move parts of your code to an include or a external component/function",msg);
	    	}
	    	throw re;
	    }
        catch(ClassFormatError e) {
        	String msg=StringUtil.emptyIfNull(e.getMessage());
        	if(StringUtil.indexOfIgnoreCase(msg, "Invalid method Code length")!=-1) {
        		throw new TemplateException("There is too much code inside the template ["+getDisplayPath()+"], Lucee was not able to break it into pieces, move parts of your code to an include or a external component/function",msg);
        	}
        	throw Caster.toPageException(e);
        }
        catch(Throwable t) {
        	throw Caster.toPageException(t);
        }
	}

	private Page _compile(ConfigWeb config,Resource classRootDir, Boolean resetCL) throws IOException, SecurityException, IllegalArgumentException, PageException {
        ConfigWebImpl cwi=(ConfigWebImpl) config;
        
        //long now; // TODO reenable keywods, double check, inspect template, watch 
        //if((getPhyscalFile().lastModified()+60000)>(now=System.currentTimeMillis()))
        //	cwi.getCompiler().watch(this,now);//SystemUtil.get
        
        
        
        byte[] barr = cwi.getCompiler().
        	compile(cwi,this,cwi.getTLDs(),cwi.getFLDs(),classRootDir,getJavaName());
        Class<?> clazz = mapping.touchPCLCollection().loadClass(getClazz(), barr,isComponent());
        try{
        	return  newInstance(clazz);
        }
        catch(Throwable t){
        	PageException pe = Caster.toPageException(t);
        	pe.setExtendedInfo("failed to load template "+getDisplayPath());
        	throw pe;
        }
    }

    private Page newInstance(Class clazz) throws SecurityException, IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
    	try{
			Constructor c = clazz.getConstructor(new Class[]{PageSource.class});
			return (Page) c.newInstance(new Object[]{this});
		}
    	// this only happens with old code from ra files
		catch(NoSuchMethodException e){
			ThreadLocalPageSource.register(this);
			try{
				return (Page) clazz.newInstance();
			}
			finally {
				ThreadLocalPageSource.release();
			}
			
			
		}
	}


	/**
     * return source path as String 
     * @return source path as String
     */
    public String getDisplayPath() {
        if(!mapping.hasArchive())  	{
        	return StringUtil.toString(getPhyscalFile(), null);
        }
        else if(isLoad(LOAD_PHYSICAL))	{
        	return StringUtil.toString(getPhyscalFile(), null);
        }
        else if(isLoad(LOAD_ARCHIVE))	{
        	return StringUtil.toString(getArchiveSourcePath(), null);
        }
        else {
            boolean pse = physcalExists();
            boolean ase = archiveExists();
            
            if(mapping.isPhysicalFirst()) {
                if(pse)return getPhyscalFile().toString();
                else if(ase)return getArchiveSourcePath();
                return getPhyscalFile().toString();
            }
            if(ase)return getArchiveSourcePath();
            else if(pse)return getPhyscalFile().toString();
            return getArchiveSourcePath();
        }
    }
    
    public boolean isComponent() {
        return ResourceUtil.getExtension(getRealpath(), "").equalsIgnoreCase(mapping.getConfig().getCFCExtension());
    }
    
    /**
	 * return file object, based on physical path and relpath
	 * @return file Object
	 */
	private String getArchiveSourcePath() {
	    return "zip://"+mapping.getArchive().getAbsolutePath()+"!"+relPath; 
	}

    /**
	 * return file object, based on physical path and relpath
	 * @return file Object
	 */
    public Resource getPhyscalFile() {
        if(physcalSource==null) {
            if(!mapping.hasPhysical()) {
            	return null;
            }
			physcalSource=ResourceUtil.toExactResource(mapping.getPhysical().getRealResource(relPath));
        }
        return physcalSource;
	}
    
    public Resource getArchiveFile() {
    	if(archiveSource==null) {
	    	if(!mapping.hasArchive()) return null;
	    	String path="zip://"+mapping.getArchive().getAbsolutePath()+"!"+relPath;
	    	archiveSource = ThreadLocalPageContext.getConfig().getResource(path);
    	}
        return archiveSource;
	}
    

    /**
	 * merge to relpath to one
	 * @param mapping 
	 * @param parentRelPath 
	 * @param newRelPath
	 * @param isOutSide 
	 * @return merged relpath
	 */
	private static String mergeRelPathes(Mapping mapping,String parentRelPath, String newRelPath, RefBoolean isOutSide) {
		//print.e("---------- mergeRelPathes ------------");
		
		parentRelPath=pathRemoveLast(parentRelPath,isOutSide);
		//print.e("->"+parentRelPath);
		//print.e("->"+newRelPath);
		
		while(newRelPath.startsWith("../")) {
			parentRelPath=pathRemoveLast(parentRelPath,isOutSide);
			newRelPath=newRelPath.substring(3);
			//print.e("->"+parentRelPath);
			//print.e("->"+newRelPath);
		}
		
		if(newRelPath.equals("..")) {
			parentRelPath=pathRemoveLast(parentRelPath,isOutSide);
			newRelPath="";
			//print.e("->"+parentRelPath);
			//print.e("->"+newRelPath);
		}
		
		
		// check if come back
		String path=parentRelPath.concat("/").concat(newRelPath);
		
		if(path.startsWith("../")) {
			int count=0;
			do {
				count++;
				path=path.substring(3);
			}while(path.startsWith("../"));
			
			String strRoot=mapping.getPhysical().getAbsolutePath().replace('\\','/');
			if(!StringUtil.endsWith(strRoot,'/')) {
				strRoot+='/';
			}
			int rootLen=strRoot.length();
			String[] arr=ListUtil.toStringArray(ListUtil.listToArray(path,'/'),"");//path.split("/");
			int tmpLen;
			for(int i=count;i>0;i--) {
				if(arr.length>i) {
					String tmp='/'+list(arr,0,i);
					tmpLen=rootLen-tmp.length();
					if(strRoot.lastIndexOf(tmp)==tmpLen && tmpLen>=0) {
						StringBuffer rtn=new StringBuffer();
						while(i<count-i) {
							count--;
							rtn.append("../");
						}
						isOutSide.setValue(rtn.length()!=0);
						//print.e("2>"+(rtn.length()==0?"/":rtn.toString())+list(arr,i,arr.length));
						return (rtn.length()==0?"/":rtn.toString())+list(arr,i,arr.length);
					}
				}
			}
		}
		//print.e("3>"+(parentRelPath.concat("/").concat(newRelPath)));
		return parentRelPath.concat("/").concat(newRelPath);
	}

	/**
	 * convert a String array to a string list, but only part of it 
	 * @param arr String Array
	 * @param from start from here
	 * @param len how many element
	 * @return String list
	 */
	private static String list(String[] arr,int from, int len) {
		StringBuffer sb=new StringBuffer();
		for(int i=from;i<len;i++) {
			sb.append(arr[i]);
			if(i+1!=arr.length)sb.append('/');
		}
		return sb.toString();
	}

	
	
	/**
	 * remove the last elemtn of a path
	 * @param path path to remove last element from it
	 * @param isOutSide 
	 * @return path with removed element
	 */
	private static String pathRemoveLast(String path, RefBoolean isOutSide) {
		if(path.length()==0) {
			isOutSide.setValue(true);
			return "..";
		}
		else if(path.endsWith("..")){
		    isOutSide.setValue(true);
			return path.concat("/..");//path+"/..";
		}
		path= path.substring(0,path.lastIndexOf('/'));
		if(StringUtil.endsWith(path, '/'))
			path=path.substring(0,path.length()-1);
		return path;
	}
	
	@Override
	public String getRealpath() {
		return relPath;
	}	
	@Override
	public String getFullRealpath() {
		if(mapping.getVirtual().length()==1 || mapping.ignoreVirtual())
			return relPath;
		return mapping.getVirtual()+relPath;
	}
	
	@Override
	public String getRealPathAsVariableString() {
		return StringUtil.toIdentityVariableName(relPath);
	}
	
	@Override
	public String getClazz() {
		if(className==null) createClassAndPackage();
		if(packageName.length()>0) return packageName+'.'+className;
		return className;
	}
	
	/**
	 * @return returns the a classname matching to filename (Example: test_cfm)
	 */
	public String getClassName() {
		if(className==null) createClassAndPackage();
		return className;
	}

    @Override
    public String getFileName() {
		if(fileName==null) createClassAndPackage();
        return fileName;
    }
	
	@Override
	public String getJavaName() {
		if(javaName==null) createClassAndPackage();
		return javaName;
	}

	/**
	 * @return returns the a package matching to file (Example: lucee.web)
	 */
	public String getPackageName() {
		if(packageName==null) createClassAndPackage();
		return packageName;
	}
	@Override
	public String getComponentName() {
		if(compName==null) createComponentName();
		return compName;
	}
	
	
	private synchronized void createClassAndPackage() {
		String str=relPath;
		StringBuffer packageName=new StringBuffer();
		StringBuffer javaName=new StringBuffer();
		
		String[] arr=ListUtil.toStringArrayEL(ListUtil.listToArrayRemoveEmpty(str,'/'));
		
		String varName;
		for(int i=0;i<arr.length;i++) {
			if(i==(arr.length-1)) {
				int index=arr[i].lastIndexOf('.');
				if(index!=-1){
					String ext=arr[i].substring(index+1);
					varName=StringUtil.toVariableName(arr[i].substring(0,index)+"_"+ext);
				}
				else varName=StringUtil.toVariableName(arr[i]);
				varName=varName+"$cf";
				className=varName.toLowerCase();
				fileName=arr[i];
			}
			else {
				varName=StringUtil.toVariableName(arr[i]);
				if(i!=0) {
				    packageName.append('.');
				}
				packageName.append(varName);
			}
			javaName.append('/');
			javaName.append(varName);
		}
		
		this.packageName=packageName.toString().toLowerCase();
		this.javaName=javaName.toString().toLowerCase();

		
		
	}
	
	

	private synchronized void createComponentName() {
		Resource res = this.getPhyscalFile();
	    String str=null;
		if(res!=null) {
			
			str=res.getAbsolutePath();
			str=str.substring(str.length()-relPath.length());
			if(!str.equalsIgnoreCase(relPath)) {
				str=relPath;
			}
		}
		else str=relPath;
	    
		StringBuffer compName=new StringBuffer();
		String[] arr;
		
		// virtual part
		if(!mapping.ignoreVirtual()) {
			arr=ListUtil.toStringArrayEL(ListUtil.listToArrayRemoveEmpty(mapping.getVirtual(),"\\/"));
			for(int i=0;i<arr.length;i++) {
				if(compName.length()>0) compName.append('.');
				compName.append(arr[i]);
			}
		}
		
		// physical part
		arr=ListUtil.toStringArrayEL(ListUtil.listToArrayRemoveEmpty(str,'/'));	
		for(int i=0;i<arr.length;i++) {
		    if(compName.length()>0) compName.append('.');
			if(i==(arr.length-1)) {
			    compName.append(arr[i].substring(0,arr[i].length()-4));
			}
			else compName.append(arr[i]);
		}
		this.compName=compName.toString();
	}

    @Override
    public Mapping getMapping() {
        return mapping;
    }

    @Override
    public boolean exists() {
    	if(mapping.isPhysicalFirst())
	        return physcalExists() || archiveExists();
	    return archiveExists() || physcalExists();
    }

    @Override
    public boolean physcalExists() {
        return ResourceUtil.exists(getPhyscalFile());
    }
    
    private boolean archiveExists() {
        if(!mapping.hasArchive())return false;
        try {
        	String clazz = getClazz();
        	if(clazz==null) return getArchiveFile().exists();
        	mapping.getClassLoaderForArchive().loadClass(clazz);
        	return true;
        } 
        catch(ClassNotFoundException cnfe){
        	return false;
        }
        catch (Exception e) {
            return getArchiveFile().exists();
        }
    }

    /**
     * return the inputstream of the source file
     * @return return the inputstream for the source from ohysical or archive
     * @throws FileNotFoundException
     */
    private InputStream getSourceAsInputStream() throws IOException {
        if(!mapping.hasArchive()) 		return IOUtil.toBufferedInputStream(getPhyscalFile().getInputStream());
        else if(isLoad(LOAD_PHYSICAL))	return IOUtil.toBufferedInputStream(getPhyscalFile().getInputStream());
        else if(isLoad(LOAD_ARCHIVE)) 	{
            StringBuffer name=new StringBuffer(getPackageName().replace('.','/'));
            if(name.length()>0)name.append("/");
            name.append(getFileName());
            
            return mapping.getClassLoaderForArchive().getResourceAsStream(name.toString());
        }
        else {
            return null;
        }
    }
    @Override
    public String[] getSource() throws IOException {
        //if(source!=null) return source;
        InputStream is = getSourceAsInputStream();
        if(is==null) return null;
        try {
        	return IOUtil.toStringArray(IOUtil.getReader(is,CharsetUtil.toCharset(getMapping().getConfig().getTemplateCharset())));
        }
        finally {
        	IOUtil.closeEL(is);
        }
    }

    @Override
    public boolean equals(Object obj) {
    	if(this==obj) return true;  
    	if(!(obj instanceof PageSource)) return false;
    	return getClassName().equals(((PageSource)obj).getClassName());
    	//return equals((PageSource)obj);
    }
    
    /**
     * is given object equal to this
     * @param other
     * @return is same
     */
    public boolean equals(PageSource other) {
        if(this==other) return true;  
        return getClassName().equals(other.getClassName());
    }

	@Override
	public PageSource getRealPage(String relPath) {
		if(relPath.equals(".") || relPath.equals(".."))relPath+='/';
	    else relPath=relPath.replace('\\','/');
	    RefBoolean _isOutSide=new RefBooleanImpl(isOutSide);
	    
	    if(relPath.indexOf('/')==0) {
		    _isOutSide.setValue(false);
		}
		else if(relPath.startsWith("./")) {
			relPath=mergeRelPathes(mapping,this.relPath, relPath.substring(2),_isOutSide);
		}
		else {
			relPath=mergeRelPathes(mapping,this.relPath, relPath,_isOutSide);
		}
		return mapping.getPageSource(relPath,_isOutSide.toBooleanValue());
	}
	
	@Override
	public final void setLastAccessTime(long lastAccess) {
		this.lastAccess=lastAccess;
	}	
	
	@Override
	public final long getLastAccessTime() {
		return lastAccess;
	}

	@Override
	public synchronized final void setLastAccessTime() {
		accessCount++;
		this.lastAccess=System.currentTimeMillis();
	}	
	
	@Override
	public final int getAccessCount() {
		return accessCount;
	}

    @Override
    public Resource getResource() {
    	Resource p = getPhyscalFile();
    	Resource a = getArchiveFile();
    	if(mapping.isPhysicalFirst()){
    		if(a==null) return p;
        	if(p==null) return a;
        	
    		if(p.exists()) return p;
    		if(a.exists()) return a;
    		return p;
    	}
    	if(p==null) return a;
    	if(a==null) return p;
    	
    	if(a.exists()) return a;
    	if(p.exists()) return p;
    	return a;
    	
    	//return getArchiveFile();
    }
    
    @Override
    public Resource getResourceTranslated(PageContext pc) throws ExpressionException {
    	Resource res = null;
    	if(!isLoad(LOAD_ARCHIVE)) res=getPhyscalFile();
    	
    	// there is no physical resource
		if(res==null){
        	String path=getDisplayPath();
        	if(path!=null){
        		if(path.startsWith("ra://"))
        			path="zip://"+path.substring(5);
        		res=ResourceUtil.toResourceNotExisting(pc, path,false,false);
        	}
        }
		return res;
    }


    public void clear() {
    	if(page!=null){
    		page=null;
    	}
    }
    
    /**
     * clear page, but only when page use the same clasloader as provided
     * @param cl
     */
    public void clear(ClassLoader cl) {
    	if(page!=null && page.getClass().getClassLoader().equals(cl)){
    		page=null;
    	}
    }

	@Override
    public String getFullClassName() {
    	String s=_getFullClassName();
    	return s;
    }
    
	public String _getFullClassName() {
		String p=getPackageName();
		if(p.length()==0) return getClassName();
		return p.concat(".").concat(getClassName());
	}
	
	public boolean isLoad() {
		return page!=null;////load!=LOAD_NONE;
	}

	@Override
	public String toString() {
		return getDisplayPath();
	}
	
	@Override
	public long sizeOf() {
		return SizeOf.size(page,0)+
		SizeOf.size(className)+
		SizeOf.size(packageName)+
		SizeOf.size(javaName)+
		SizeOf.size(fileName)+
		SizeOf.size(compName)+
		SizeOf.size(lastAccess)+
		SizeOf.size(accessCount);
	}

	public static PageSource best(PageSource[] arr) {
		if(ArrayUtil.isEmpty(arr)) return null;
		if(arr.length==1)return arr[0];
		for(int i=0;i<arr.length;i++) {
			if(pageExist(arr[i])) return arr[i];
		}
		return arr[0];
	}

	public static boolean pageExist(PageSource ps) {
		return (ps.getMapping().isTrusted() && ((PageSourceImpl)ps).isLoad()) || ps.exists();
	}

	public static Page loadPage(PageContext pc,PageSource[] arr,Page defaultValue) throws PageException {
		if(ArrayUtil.isEmpty(arr)) return null;
		Page p;
		for(int i=0;i<arr.length;i++) {
			p=arr[i].loadPage(pc,(Page)null);
			if(p!=null) return p;
		}
		return defaultValue;
	}

	public static Page loadPage(PageContext pc,PageSource[] arr) throws PageException {
		if(ArrayUtil.isEmpty(arr)) return null;
		
		Page p;
		for(int i=0;i<arr.length;i++) {
			p=arr[i].loadPage(pc,(Page)null);
			if(p!=null) return p;
		}
		throw new MissingIncludeException(arr[0]);
	}
	
	
	
}