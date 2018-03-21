/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.AccessControl;
import com.qizx.api.Library;
import com.qizx.api.QizxException;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.accesscontrol.ACLAccessControl;

import java.io.IOException;

/**
 * Return Access Control information related to a Library Member.
 */
public class GetAclRequest extends QizxRequestBase
{
    public String getName()
    {
        return "getacl";
    }

    public void handleGet()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String path = getPathParam();
        String scopeParam = getParameter("scope", "local");
        boolean inheritedScope = "inherit".equalsIgnoreCase(scopeParam);
        if(!inheritedScope && !"local".equalsIgnoreCase(scopeParam))
            throw new RequestException(BAD_REQUEST,
                            "parameter 'scope' should be 'local' or 'inherit'");

        try {
            Library lib = acquireLibSession(libName);
            
            AccessControl ac = lib.getAccessControl();
            if(!(ac instanceof ACLAccessControl)) {
                throw new RequestException(ACCESS, "not supported");
            }
            ACLAccessControl acla = (ACLAccessControl) ac;
            response.setContentType(ACLAccessControl.MIME_TYPE);
            println(acla.getAccessRights(path, inheritedScope));
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
