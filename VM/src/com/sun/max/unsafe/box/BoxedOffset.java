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
/*VCSID=657411d6-dd22-426d-8e2e-eefbaaf8736c*/
package com.sun.max.unsafe.box;

import com.sun.max.unsafe.*;

/**
 * Boxed version of Offset.
 * 
 * @author Bernd Mathiske
 */
public final class BoxedOffset extends Offset implements UnsafeBox {

    private long _nativeWord;

    public BoxedOffset(UnsafeBox unsafeBox) {
        _nativeWord = unsafeBox.nativeWord();
    }

    public BoxedOffset(long value) {
        _nativeWord = value;
    }

    public BoxedOffset(int value) {
        _nativeWord = value;
    }

    public long nativeWord() {
        return _nativeWord;
    }
}
