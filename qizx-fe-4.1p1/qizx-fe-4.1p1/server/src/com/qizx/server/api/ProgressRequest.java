/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.QizxDriver.LongAction;

import java.io.IOException;

/**
 * Return progress information about a long task such as backup, commit etc.
 */
public class ProgressRequest extends QizxRequestBase
{
    public String getName()
    {
        return "progress";
    }

    public void handleGet()
        throws RequestException, IOException
    {
        String idParam = getParameter("id");

        QizxDriver driver = requireQizxDriver();

        response.setContentType(MIME_PLAIN_TEXT);
        LongAction action = driver.findAction(idParam);
        if (action == null)
            throw new RequestException(NOT_FOUND, "no such action in progress: " + idParam);
        
        println(action.getProgress());
    }
}
