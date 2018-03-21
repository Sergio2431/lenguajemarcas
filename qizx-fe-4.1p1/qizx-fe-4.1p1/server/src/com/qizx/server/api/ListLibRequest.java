/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.LibraryManager;
import com.qizx.api.QizxException;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;

import java.io.IOException;

/**
 * Returns the names of the XML Libraries managed by the server.
 */
public class ListLibRequest extends QizxRequestBase
{
    public String getName()
    {
        return "listlib";
    }

    public void handleGet()
        throws RequestException, IOException
    {
        try {
            LibraryManager libMan = requireEngine();
            
            String[] libs = libMan.listLibraries();
            response.setContentType(MIME_PLAIN_TEXT);
            for (int i = 0; i < libs.length; i++) {
                println(libs[i]);
            }
            output.flush();
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }

    public void handlePost()
        throws RequestException, IOException
    {
        handleGet();
    }
}
