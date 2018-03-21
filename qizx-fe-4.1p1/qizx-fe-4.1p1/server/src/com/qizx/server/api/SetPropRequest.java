/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.Expression;
import com.qizx.api.ItemSequence;
import com.qizx.api.ItemType;
import com.qizx.api.Library;
import com.qizx.api.LibraryMember;
import com.qizx.api.QizxException;
import com.qizx.apps.util.Property;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.xdm.DocumentParser;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

/**
 * Modify, create or remove properties of a Library member.
 */
public class SetPropRequest extends QizxRequestBase
{
    public String getName()
    {
        return "setprop";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String path = getPathParam();
        
        String nameParam = getParameter("name");
        if(nameParam == null)
            throw new RequestException(BAD_REQUEST, "parameter 'name' should not be empty");
        String typeParam = getParameter("type", "string");
        String valueParam = getParameter("value");
        int rank = getParameter("name1") != null? 1 : 2;
        
        try {
            Library lib = acquireLibSession(libName);
            response.setContentType(MIME_PLAIN_TEXT);

            LibraryMember member = requireMember(lib, path);
            for(; ; ++rank)
            {
                Object value = valueParam;
                ItemType type = lib.getType(typeParam);
                if(Property.EXPRESSION.equals(typeParam)) {
                    Expression exp = lib.compileExpression(valueParam);
                    ItemSequence res = exp.evaluate();
                    if(res.moveToNextItem()) {
                        value = res.getCurrentItem();
                    } // ignore remaining items...
                }
                else if(Property.NODE.equals(typeParam)) {
                    try {
                        value = DocumentParser.parse(new InputSource(new StringReader(valueParam)));
                    }
                    catch (SAXException e) {
                        throw new RequestException("XMLData", e);
                    }
                }
                else if(type == null)
                    throw new RequestException(BAD_REQUEST, "invalid property type '"+typeParam+"'");
                else {
                    value = lib.createItem(valueParam, type);
                }
                
                log("value "+value+" class "+(value != null? value.getClass() : null));
                member.setProperty(nameParam, value);
                
                // more?
                nameParam = getParameter("name" + rank);
                if(nameParam == null)
                    break;
                typeParam = getParameter("type" + rank);
                valueParam = getParameter("value" + rank);
            }            
            lib.commit();
            println(member.getPath());
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
