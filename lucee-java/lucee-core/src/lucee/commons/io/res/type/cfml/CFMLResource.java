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
package lucee.commons.io.res.type.cfml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lucee.commons.io.CharsetUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.ResourceProvider;
import lucee.commons.io.res.util.ResourceSupport;
import lucee.commons.io.res.util.ResourceUtil;
import lucee.runtime.Component;
import lucee.runtime.PageContext;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.PageException;
import lucee.runtime.exp.PageRuntimeException;
import lucee.runtime.op.Caster;
import lucee.runtime.type.dt.DateTime;
import lucee.runtime.type.dt.DateTimeImpl;

public class CFMLResource extends ResourceSupport {

	private static final long serialVersionUID = 7693378761683536212L;
	
	private static final Object[] ZERO_ARGS = new Object[0];
	private CFMLResourceProvider provider;
	private Component cfc;

	public CFMLResource(CFMLResourceProvider provider,Component cfc) {
		this.provider=provider;
		this.cfc=cfc;
	}

	@Override
	public boolean isReadable() {
		return provider.callbooleanRTE(null,cfc,"isReadable",ZERO_ARGS);
	}

	@Override
	public boolean isWriteable() {
		return provider.callbooleanRTE(null,cfc,"isWriteable",ZERO_ARGS);
	}

	@Override
	public void remove(boolean force) throws IOException {
		provider.callRTE(null,cfc, "remove", new Object[]{force?Boolean.TRUE:Boolean.FALSE});
	}
	

	@Override
	public boolean exists() {
		return provider.callbooleanRTE(null,cfc,"exists",ZERO_ARGS);
	}

	@Override
	public String getName() {
		return provider.callStringRTE(null,cfc,"getName",ZERO_ARGS);
	}

	@Override
	public String getParent() {
		return provider.callStringRTE(null,cfc,"getParent",ZERO_ARGS);
	}

	@Override
	public Resource getParentResource() {
		return provider.callResourceRTE(null, cfc, "getParentResource", ZERO_ARGS,true);
	}

	@Override
	public Resource getRealResource(String realpath) {
		return provider.callResourceRTE(null, cfc, "getRealResource", new Object[]{realpath},true);
	}

	@Override
	public String getPath() {
		return provider.callStringRTE(null,cfc,"getPath",ZERO_ARGS);
	}

	@Override
	public boolean isAbsolute() {
		return provider.callbooleanRTE(null,cfc,"isAbsolute",ZERO_ARGS);
	}

	@Override
	public boolean isDirectory() {
		return provider.callbooleanRTE(null,cfc,"isDirectory",ZERO_ARGS);
	}

	@Override
	public boolean isFile() {
		return provider.callbooleanRTE(null,cfc,"isFile",ZERO_ARGS);
	}

	@Override
	public long lastModified() {
		PageContext pc = ThreadLocalPageContext.get();
		try{
			DateTime date = Caster.toDate(provider.call(pc,cfc,"lastModified",ZERO_ARGS), true, pc.getTimeZone());
			return date.getTime();
		}
		catch(PageException pe){
			throw new PageRuntimeException(pe);
		}
		
	}

	@Override
	public long length() {
		return provider.calllongRTE(null,cfc,"length",ZERO_ARGS);
	}

	@Override
	public Resource[] listResources() {
		return provider.callResourceArrayRTE(null, cfc, "listResources", ZERO_ARGS);
	}

	@Override
	public boolean setLastModified(long time) {
		PageContext pc = ThreadLocalPageContext.get();
		return provider.callbooleanRTE(pc,cfc,"setLastModified",new Object[]{new DateTimeImpl(pc, time, false)});
	}

	@Override
	public boolean setWritable(boolean writable) {
		return provider.callbooleanRTE(null,cfc,"setWritable",new Object[]{writable?Boolean.TRUE:Boolean.FALSE});
	}

	@Override
	public boolean setReadable(boolean readable) {
		return provider.callbooleanRTE(null,cfc,"setReadable",new Object[]{readable?Boolean.TRUE:Boolean.FALSE});
	}

	@Override
	public void createFile(boolean createParentWhenNotExists) throws IOException {
		ResourceUtil.checkCreateFileOK(this,createParentWhenNotExists);
		provider.lock(this);
		try {
			provider.callRTE(null,cfc,"createFile",new Object[]{createParentWhenNotExists?Boolean.TRUE:Boolean.FALSE});
		}
		finally {
			provider.unlock(this);
		}
	}
	
	@Override
	public void createDirectory(boolean createParentWhenNotExists) throws IOException {
		ResourceUtil.checkCreateDirectoryOK(this,createParentWhenNotExists);
		provider.lock(this);
		try {
			provider.callRTE(null,cfc,"createDirectory",new Object[]{createParentWhenNotExists?Boolean.TRUE:Boolean.FALSE});
		}
		finally {
			provider.unlock(this);
		}
		
	}

	@Override
	public InputStream getInputStream() throws IOException {
		ResourceUtil.checkGetInputStreamOK(this);
		
		try {
			Object obj;
			if(provider.isUseStreams())  
				obj = provider.call(null, cfc, "getInputStream", ZERO_ARGS);
			else
				obj = provider.call(null, cfc, "getBinary", ZERO_ARGS);
			if(obj==null) obj=new byte[0];
			return Caster.toInputStream(obj,null);
		} 
		catch (PageException pe) {
			throw new PageRuntimeException(pe);
		}
	}

	@Override
	public OutputStream getOutputStream(boolean append) throws IOException {
		try {
			if(provider.isUseStreams()) {
				Object obj = provider.call(null, cfc, "getOutputStream", new Object[]{append?Boolean.TRUE:Boolean.FALSE});
				return Caster.toOutputStream(obj);
			}
			return new CFMLResourceOutputStream(this);
		} 
		catch (PageException pe) {
			throw new PageRuntimeException(pe);
		}
	}
	
	public void setBinary(Object obj) throws PageException {
		byte[] barr;
		if(obj instanceof CharSequence) {
			CharSequence cs=(CharSequence) obj;
			String str = cs.toString();
			barr=str.getBytes(CharsetUtil.UTF8);
		}
		else {
			barr=Caster.toBinary(obj);
		}
		provider.call(null, cfc, "setBinary", new Object[]{barr});
	}

	@Override
	public ResourceProvider getResourceProvider() {
		return provider;
	}

	@Override
	public int getMode() {
		return provider.callintRTE(null,cfc,"getMode",ZERO_ARGS);
	}

	@Override
	public void setMode(int mode) throws IOException {
		provider.callRTE(null,cfc,"setMode",new Object[]{Caster.toDouble(mode)});
	}

}
