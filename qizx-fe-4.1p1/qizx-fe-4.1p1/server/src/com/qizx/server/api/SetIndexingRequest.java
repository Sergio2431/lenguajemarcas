/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.Indexing;
import com.qizx.api.Library;
import com.qizx.api.QizxException;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;

import org.xml.sax.InputSource;

import java.io.IOException;

/**
 * Define or redefine the Indexing Specification of the Library.
 */
public class SetIndexingRequest extends QizxRequestBase
{
    public String getName()
    {
        return "setindexing";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();

        InputSource indexingSpec = openXMLSource("indexing");
        if (indexingSpec == null)
            throw new RequestException(BAD_REQUEST, "no specification in parameter 'indexing'");
        
        try {
            Library lib = acquireLibSession(libName);
            response.setContentType(MIME_PLAIN_TEXT);

            Indexing xing = new Indexing();
            xing.parse(indexingSpec);
            
            // The Qizx API already demands write-access to root collection,
            // but we add this constraint. 
            checkAdminRole(getDriver());
            
            lib.setIndexing(xing);
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
