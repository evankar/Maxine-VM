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
/*VCSID=e58ef526-3dd4-4bb0-af11-4cff10a30db9*/
package com.sun.max.asm.dis;

import java.io.*;

/**
 * An {@code InlineDataDecoder} knows which positions in an instruction stream being disassembled contains inline data.
 *
 * @author Doug Simon
 */
public interface InlineDataDecoder {

    /**
     * Decodes the data (if any) from the current read position of a given stream.
     * 
     * @param currentPosition
     *                the stream's current read position with respect to the start of the stream
     * @param stream
     *                the instruction stream being disassembled
     * @param decodedDataBuffer
     *                if non-null, then the decoded bytes will also be written to this buffer
     * @return the number of bytes of data decoded from the stream
     */
    int decodeData(int currentPosition, BufferedInputStream stream, OutputStream decodedDataBuffer) throws IOException;
}
