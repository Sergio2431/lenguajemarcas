/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.QizxException;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;

import java.io.IOException;

/**
 * Create a new Library in the server.
 */
public class MkLibRequest extends QizxRequestBase
{
    public String getName()
    {
        return "mklib";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String nameParam = getParameter("name");

        try {
            LibraryManager engine = requireEngine();
            response.setContentType(MIME_PLAIN_TEXT);
            
            checkAdminRole(getDriver());
            engine.createLibrary(nameParam, null);
            getDriver().changedLibraryList();
            println(nameParam);
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
