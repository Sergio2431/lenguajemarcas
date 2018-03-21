/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.QizxException;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;

import java.io.IOException;

/**
 * Utility request: close and open the database.
 */
public class ServerControlRequest extends QizxRequestBase
{
    private static final String CMD_STATUS = "status";
    private static final String CMD_START = "online";
    private static final String CMD_STOP = "offline";
    private static final String CMD_RESTART = "restart";
    private static final String CMD_RELOAD = "reload";

    public String getName()
    {
        return "server";
    }

    public void handleGet() // status only
        throws RequestException, IOException
    {
        QizxDriver qizxd = requireQizxDriver();
        response.setContentType(MIME_PLAIN_TEXT);
        println(getStatus(qizxd));
    }

    private String getStatus(QizxDriver qizxd)
    {
        return qizxd.isRunning() ? "online" : "offline";
    }

    public void handlePost()
        throws RequestException, IOException
    {
        String command = getParameter("command", CMD_STATUS);
        try {
            QizxDriver qizxd = requireQizxDriver();
            
            if(!CMD_STATUS.equals(command))
                checkAdminRole(getDriver());

            response.setContentType(MIME_PLAIN_TEXT);
            if(CMD_START.equals(command)) {
                qizxd.start();
            }
            else if(CMD_STOP.equals(command)) {
                qizxd.stop();
            }
            else if(CMD_RELOAD.equals(command)) {
                qizxd.reload(servlet);
            }
            
            println(getStatus(qizxd));
        }
        catch (QizxException e) {
            throw new RequestException(e);
        }
    }
}
