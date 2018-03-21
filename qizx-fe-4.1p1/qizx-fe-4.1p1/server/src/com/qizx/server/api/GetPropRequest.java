/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.*;
import com.qizx.api.util.XMLSerializer;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;

import java.io.IOException;
import java.util.HashSet;

/**
 * Get the properties of a Library member or of a hierarchy of members.
 */
public class GetPropRequest extends QizxRequestBase
{
    public String getName()
    {
        return "getprop";
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
        int depth = getIntParameter("depth", 0); //Integer.MAX_VALUE);
        String properties = getParameter("properties");
        HashSet propNames = parseNameList(properties);
        
        try {
            Library lib = acquireLibSession(libName);
            
            response.setContentType(MIME_XML);

            LibraryMember member = requireMember(lib, path);

            XMLSerializer out = new XMLSerializer(output, "UTF-8");
            QName NM_GETPROP = lib.getQName("getprop");

            out.putDocumentStart();
            out.putElementStart(NM_GETPROP);

            recPutProps(member, out, depth, propNames);
            
            out.putElementEnd(NM_GETPROP);
            out.putDocumentEnd();
            out.flush();
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }

    private void recPutProps(LibraryMember member, XMLSerializer serial,
                             int depth, HashSet propNames)
        throws DataModelException
    {
        RESTAPIServlet.putProperties(member, serial, propNames);
        if(!member.isCollection() || --depth < 0)
            return;
        LibraryMemberIterator children = ((Collection) member).getChildren();
        for(; children.moveToNextMember(); )
            recPutProps(children.getCurrentMember(), serial, depth, propNames);
    }
}
