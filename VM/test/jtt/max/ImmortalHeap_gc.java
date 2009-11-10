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
/*
 * @Harness: java
 * @Runs: (1)=true; (10)=true;
 */
/**
 * @author Hannes Payer
 */
package jtt.max;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.heap.*;


public final class ImmortalHeap_gc {
    private ImmortalHeap_gc() {
    }

    @UNSAFE
    public static boolean test(int nrObjects) {
        String[] strings;
        ImmortalMemoryRegion immortalMemoryRegion = ImmortalHeap.getImmortalHeap();
        Pointer oldMark = immortalMemoryRegion.mark();

        try {
            Heap.enableImmortalMemoryAllocation();
            strings = new String[nrObjects];
        } finally {
            Heap.disableImmortalMemoryAllocation();
        }

        if (immortalMemoryRegion.mark().equals(oldMark)) {
            return false;
        }

        oldMark = immortalMemoryRegion.mark();

        for (int i = 0; i < nrObjects; i++) {
            strings[i] = new String("" + i);
        }

        if (!immortalMemoryRegion.mark().equals(oldMark)) {
            return false;
        }

        System.gc();

        if (!immortalMemoryRegion.mark().equals(oldMark)) {
            return false;
        }

        String expected = "";
        String result = "";
        for (int i = 0; i < nrObjects; i++) {
            expected += i;
            result += strings[i];
        }

        if (expected.equals(result)) {
            return true;
        }

        return false;
    }

}
