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
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.accesscontrol.ACLAccessControl;

import java.io.IOException;

/**
 * Define or remove Access Control information related to a Library Member.
 */
public class SetAclRequest extends QizxRequestBase
{
    public String getName()
    {
        return "setacl";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String libName = getLibraryParam();
        String aclParam = getParameter("acl");
        try {
            Library lib = acquireLibSession(libName);
            response.setContentType(MIME_PLAIN_TEXT);
            AccessControl ac = lib.getAccessControl();
            if(!(ac instanceof ACLAccessControl))
                return;
            checkAdminRole(getDriver());
            ACLAccessControl acla = (ACLAccessControl) ac;
            acla.setAccessRights(aclParam);
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
