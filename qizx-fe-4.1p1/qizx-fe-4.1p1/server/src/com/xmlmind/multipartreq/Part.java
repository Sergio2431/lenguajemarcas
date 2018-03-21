/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 * Copyright (c) 2009 Pixware SARL. 
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind MultipartRequest project.
 * For conditions of distribution and use, see the accompanying legal.txt file.
 */
package com.xmlmind.multipartreq;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;

/**
 * This class represents a part or form item that was received 
 * within a <tt>multipart/form-data</tt> POST request. 
 */
public interface Part {
    /**
     * Returns the name of this part.
     */
    String getName();

    /**
     * Returns the content type of this part. 
     */
    String getContentType();

    /**
     * Returns the size of this parts, in bytes.
     */
    long getSize();

    /**
     * Returns a possibly empty array containing the header names 
     * of this Part.
     */
    String[] getHeaderNames();

    /**
     * Returns the value of the specified header. 
     * <p>If the Part did not include a header of the specified name, 
     * this method returns <code>null</code> 
     * <p>If there are multiple headers with the same name, this method
     * returns the first header in the part. 
     * <p>A header name is case insensitive. 
     */
    String getHeader(String name);

    /**
     * Returns the values of the specified header. 
     * <p>If the Part did not include a header of the specified name, 
     * this method returns an empty array.
     * <p>A header name is case insensitive. 
     */
    String[] getHeaders(String name);

    /**
     * Returns the contents of this part.
     * 
     * @exception IOException if an error occurs in retrieving 
     * the content as an InputStream
     */
    InputStream getInputStream()
        throws IOException;

    /**
     * A convenience method to write this part to disk.
     * <p>This method is not guaranteed to succeed if called more 
     * than once for the same part. This allows a particular implementation 
     * to use, for example, file renaming, where possible, rather 
     * than copying all of the underlying data, thus gaining a 
     * significant performance benefit. 
     *
     * @param fileName absolute or relative path of the file to which the part 
     * will be written. 
     * <p>The file is created relative to the location specified 
     * in the MultipartConfig.
     * <p>If this file already exists, it will be overwritten.
     * @exception IOException if an error occurs during this operation
     * @see MultipartConfig
     */
    void write(String fileName)
        throws IOException;

    /**
     * Deletes the underlying storage for this part, including deleting 
     * any associated temporary disk file. 
     * 
     * @exception IOException if an error occurs during this operation
     */
    void delete()
        throws IOException;
}
