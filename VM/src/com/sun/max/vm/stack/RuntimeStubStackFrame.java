/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
/*VCSID=c4729de6-f115-4961-86fb-4afbc010598b*/
package com.sun.max.vm.stack;

import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;

/**
 * A runtime stub stack frame.
 *
 * @author Laurent Daynes
 */
public class RuntimeStubStackFrame extends StackFrame {
    private RuntimeStub _stub;

    public RuntimeStubStackFrame(StackFrame callee, RuntimeStub stub, Pointer instructionPointer, Pointer framePointer, Pointer stackPointer) {
        super(callee, instructionPointer, framePointer, stackPointer);
        _stub = stub;
    }

    @Override
    public boolean isJavaStackFrame() {
        return false;
    }

    @Override
    public boolean isSameFrame(StackFrame stackFrame) {
        if (stackFrame instanceof RuntimeStubStackFrame) {
            final RuntimeStubStackFrame runtimeStubStackFrame = (RuntimeStubStackFrame) stackFrame;
            return _stub.equals(runtimeStubStackFrame._stub) && stackFrame.stackPointer().equals(stackPointer());
        }
        return false;
    }

    @Override
    public TargetMethod targetMethod() {
        return null;
    }

    public RuntimeStub stub() {
        return _stub;
    }

}
