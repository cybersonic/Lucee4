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
package lucee.transformer.bytecode.statement;

import lucee.transformer.bytecode.BytecodeContext;
import lucee.transformer.bytecode.BytecodeException;
import lucee.transformer.bytecode.Position;
import lucee.transformer.bytecode.util.Types;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public final class Abort extends StatementBaseNoFinal {

	private static final Type ABORT = Type.getType(lucee.runtime.exp.Abort.class);
	
	// ExpressionException newInstance(int)
	private static final Method NEW_INSTANCE =  new Method(
			"newInstance",
			ABORT,
			new Type[]{Types.INT_VALUE});

	
	public Abort(Position start, Position end) {
		super(start,end);
	}

	/**
	 *
	 * @see lucee.transformer.bytecode.statement.StatementBase#_writeOut(lucee.transformer.bytecode.BytecodeContext)
	 */
	public void _writeOut(BytecodeContext bc) throws BytecodeException {
		GeneratorAdapter adapter = bc.getAdapter();
		adapter.push(lucee.runtime.exp.Abort.SCOPE_PAGE);
		adapter.invokeStatic(ABORT, NEW_INSTANCE);
		adapter.throwException();
		
	}
}