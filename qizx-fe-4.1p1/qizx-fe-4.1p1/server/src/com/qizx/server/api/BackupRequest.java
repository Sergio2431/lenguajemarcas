/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package com.qizx.server.api;

import com.qizx.api.DataModelException;
import com.qizx.api.Library;
import com.qizx.server.util.RequestException;
import com.qizx.server.util.QizxDriver;
import com.qizx.server.util.QizxRequestBase;
import com.qizx.server.util.QizxDriver.LongAction;

import java.io.File;
import java.io.IOException;

/**
 * Start backup of a Library. Long operation returning a Progress Identifier.
 */
public class BackupRequest extends QizxRequestBase
{
    public String getName()
    {
        return "backup";
    }

    public void handlePost()
        throws RequestException, IOException
    {        
        String libName = getLibraryParam();
        String path = getPathParam();
        final File location = new File(path);

        try {
            final QizxDriver driver = requireQizxDriver();
            boolean doAll = "*".equals(libName);

            LongAction action;
            if(doAll) {
                checkAdminRole(getDriver());
                action = driver.new LongAction(null, "backup all") {
                    public void run() {
                        try {
                            backupAllLibraries(location, BackupRequest.this);
                            finishedAction();
                        }
                        catch (Throwable e) {
                            abortedAction(e);
                        }
                    }
                };
            }
            else {
                Library lib = acquireLibSession(libName);
                checkAdminRole(getDriver());
                action = driver.new LongAction(lib, "backup " + lib.getName()) {
                    public void run() {
                        try {
                            library.backup(location);
                            finishedAction();
                        }
                        catch (Throwable e) {
                            abortedAction(e);
                        }
                    }
                };
                // Attention must not be cleaned up, since used by long action
                libSession = null;
                // Not clear what happens 
            }
            response.setContentType(MIME_PLAIN_TEXT);
            println(action.getId());
            action.start();
        }
        catch (DataModelException e) {
            throw new RequestException(e);
        }
        catch (IOException e) {
            throw new RequestException(SERVER, e);
        }
    }
}
