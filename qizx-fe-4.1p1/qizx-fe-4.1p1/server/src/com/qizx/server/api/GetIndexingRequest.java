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
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;

import java.io.IOException;

/**
 * Get the Indexing Specification of the Library as an XML document.
 */
public class GetIndexingRequest extends QizxRequestBase
{
    public String getName()
    {
        return "getindexing";
    }

    public void handleGet()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String path = getPathParam();

        try {
            Library lib = acquireLibSession(libName);
            response.setContentType(MIME_XML);
            
            XMLSerializer serial = new XMLSerializer(output, "UTF-8");
            Indexing specs = lib.getIndexing();
            specs.export(serial);
            serial.flush();
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
