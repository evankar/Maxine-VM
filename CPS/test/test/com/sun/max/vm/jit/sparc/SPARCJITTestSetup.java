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
package test.com.sun.max.vm.jit.sparc;

import junit.framework.*;
import test.com.sun.max.vm.cps.sparc.*;
import test.com.sun.max.vm.jit.*;

import com.sun.max.asm.*;
import com.sun.max.platform.*;
import com.sun.max.vm.*;
import com.sun.max.vm.cps.jit.*;
import com.sun.max.vm.cps.jit.sparc.*;
import com.sun.max.vm.template.*;

/**
 * Test setup for JIT tests on SPARC.
 *
 * @author Laurent Daynes
 */
public class SPARCJITTestSetup extends SPARCTranslatorTestSetup  implements JITTestSetup {
    public SPARCJITTestSetup(Test test) {
        super(test);
    }

    @Override
    protected VMConfiguration createVMConfiguration() {
        return VMConfigurations.createStandardJit(BuildLevel.DEBUG, Platform.host().constrainedByInstructionSet(InstructionSet.SPARC));
    }

    public JitCompiler newJitCompiler(TemplateTable templateTable) {
        return new SPARCJitCompiler(VMConfiguration.target(), templateTable);
    }

    public boolean disassembleCompiledMethods() {
        return true;
    }
}
