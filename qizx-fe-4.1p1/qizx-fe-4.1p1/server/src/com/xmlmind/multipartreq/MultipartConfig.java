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

/**
 * Specifies how to process <tt>multipart/form-data</tt> requests.
 */
public class MultipartConfig {
    /**
     * The maximum size allowed for <tt>multipart/form-data</tt> requests.
     * A negative or null value is understood as: no limit.
     */
    public final long maxRequestSize;

    /**
     * The maximum size allowed for uploaded files.
     * A negative or null value is understood as: no limit.
     */
    public final long maxFileSize;

    /**
     * The size threshold after which the file will be written to disk.
     * A negative or null value is understood as: no threshold.
     */
    public final int fileSizeThreshold;

    /**
     * The directory location where files will be stored.
     * A <code>null</code> or empty string specifies 
     * the current working directory.
     */
    public final String location;

    // -----------------------------------------------------------------------

    /**
     * Constructs a MultipartConfig initialized using specified parameters.
     */
    public MultipartConfig(long maxRequestSize, long maxFileSize, 
                           int fileSizeThreshold, String location) {
        this.maxRequestSize = maxRequestSize;
        this.maxFileSize = maxFileSize;
        this.fileSizeThreshold = fileSizeThreshold;
        this.location = location;
    }
}
