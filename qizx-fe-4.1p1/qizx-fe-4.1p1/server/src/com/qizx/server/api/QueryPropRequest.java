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
import com.qizx.api.Expression;
import com.qizx.api.Library;
import com.qizx.api.LibraryMemberIterator;
import com.qizx.api.QName;
import com.qizx.api.QizxException;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;

import java.io.IOException;
import java.util.HashSet;

/**
 * Find Documents or Collections through their metadata properties.
 */
public class QueryPropRequest extends QizxRequestBase
{
    public String getName()
    {
        return "queryprop";
    }

    public void handleGet()
        throws RequestException, IOException
    {
        handlePost();
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();

        String path = getPathParam();
        if(path == null)
            path = "/";
        String queryParam = getParameter("query");
        String properties = getParameter("properties", "path,nature");
        HashSet propNames = parseNameList(properties);
       
        try {
            Library lib = acquireLibSession(libName);
            
            Expression q = lib.compileExpression(queryParam);
            Collection root = lib.getCollection(path);
            if(root == null)
                throw new RequestException(NOT_FOUND, "root collection '" + path
                           + "' does not exist");
            response.setContentType(MIME_XML);
            QName NAME = lib.getQName("queryprop");
            XMLSerializer out = new XMLSerializer(output, "UTF-8");

            out.putDocumentStart();
            out.putElementStart(NAME);
           
            LibraryMemberIterator members = root.queryProperties(q);
            for(; members.moveToNextMember(); ) {
                RESTAPIServlet.putProperties(members.getCurrentMember(), out, propNames);
            }

            out.putElementEnd(NAME);
            out.putDocumentEnd();
            out.flush();
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
