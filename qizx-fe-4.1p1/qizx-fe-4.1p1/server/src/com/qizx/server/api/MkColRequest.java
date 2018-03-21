/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.Collection;
import com.qizx.api.DataModelException;
import com.qizx.api.Library;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.util.basic.PathUtil;

import java.io.IOException;

/**
 * Create a Collection.
 */
public class MkColRequest extends QizxRequestBase
{
    public String getName()
    {
        return "mkcol";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String path = getPathParam();
        if(path == null)
            requiredParam("path");
        boolean parents = getBooleanParameter("parents", true);

        try {
            Library lib = acquireLibSession(libName);
            response.setContentType(MIME_PLAIN_TEXT);
            
            if(!parents) {
                // need to check parent: the API doesnt care
                String parentPath = PathUtil.getParentPath(path);
                if(lib.getMember(parentPath) == null)
                    throw new RequestException(NOT_FOUND,
                               "parent collection does not exist " + parentPath);
            }
            Collection coll = lib.createCollection(path);
            lib.commit();
            println(coll.getPath());
        }
        catch (DataModelException e) {
            throw new RequestException(XML_DATA, e);
        }
    }
}
