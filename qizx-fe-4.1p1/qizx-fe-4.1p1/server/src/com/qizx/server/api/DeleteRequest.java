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
import com.qizx.api.LibraryMember;
import com.qizx.api.QizxException;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;

import java.io.IOException;

/**
 * Delete a Document or a Collection.
 */
public class DeleteRequest extends QizxRequestBase
{
    public String getName()
    {
        return "delete";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String path = getPathParam();

        try {
            Library lib = acquireLibSession(libName);
            response.setContentType(MIME_PLAIN_TEXT);
            LibraryMember member = lib.getMember(path);
            if(member == null)
                println("");
            else {
                member.delete();
                lib.commit();
                println(member.getPath());
            }
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
