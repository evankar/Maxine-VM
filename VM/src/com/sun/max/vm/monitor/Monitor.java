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
/*VCSID=4b28722f-0ad8-46c7-aa2b-7f1b69cce45a*/
package com.sun.max.vm.monitor;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.MaxineVM.*;
import com.sun.max.vm.thread.*;

/**
 * @author Bernd Mathiske
 */
public final class Monitor {
    private Monitor() {
    }

    /**
     * Determines if monitor activity should be traced at a level useful for debugging.
     */
    @INLINE
    public static boolean traceMonitors() {
        return _traceMonitors;
    }

    private static boolean _traceMonitors;

    private static final VMOption _traceMonitorsOption = new VMOption("-XX:TraceMonitors", "", Phase.STARTING) {
        @Override
        public boolean parse(Pointer optionStart) {
            _traceMonitors = true;
            return super.parse(optionStart);
        }
    };

    @UNSAFE
    @FOLD
    private static MonitorScheme monitorScheme() {
        return VMConfiguration.hostOrTarget().monitorScheme();
    }

    @INLINE
    public static int makeHashCode(Object object) {
        return monitorScheme().makeHashCode(object);
    }

    @INLINE
    public static void enter(Object object) {
        monitorScheme().monitorEnter(object);
    }

    @INLINE
    public static void exit(Object object) {
        monitorScheme().monitorExit(object);
    }

    @NEVER_INLINE
    public static void noninlineEnter(Object object) {
        enter(object);
    }

    @NEVER_INLINE
    public static void noninlineExit(Object object) {
        exit(object);
    }

    @INLINE
    public static boolean threadHoldsMonitor(Object object, VmThread thread) {
        return monitorScheme().threadHoldsMonitor(object, thread);
    }
}
