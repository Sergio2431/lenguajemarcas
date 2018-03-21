/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx Free_Engine-4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx Free_Engine-4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx Free_Engine-4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx Free_Engine-4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package delete;

import java.io.File;
import com.qizx.api.QizxException;
import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.LibraryManagerFactory;

public class Delete
{
    public static void main(String[] args)
        throws QizxException
    {
        if (args.length < 2) {
            usage();
            /*NOTREACHED*/
        }
        File storageDir = new File(args[0]);
        String libName = args[1];

        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        LibraryManager libManager = factory.openLibraryGroup(storageDir);

        if (args.length == 2) {
            verbose("Deleting library '" + libName + "'...");
            if (!libManager.deleteLibrary(libName)) {
                warning("Library '" + libName + "' not found");
            }
            libManager.closeAllLibraries(10000 /*ms*/);
        }
        else {
            Library lib = libManager.openLibrary(libName);

            try {
                for (int i = 2; i < args.length; ++i) {
                    String path = args[i];

                    verbose("Deleting member '" + path + "' of library '"
                            + libName + "'...");
                    if (!lib.deleteMember(path)) {
                        warning("Member '" + path + "' of library '" + libName
                                + "' not found");
                    }
                }

                verbose("Committing changes...");
                lib.commit();
            }
            finally {
                shutdown(lib, libManager);
            }
        }
    }

    private static void usage()
    {
        System.err.println(
            "usage: java Delete libraries_storage_dir library_name path+\n"
          + "  libraries_storage_dir Directory containing libraries.\n"
          + "  library_name Name of library containing documents\n"
          + "      and collections to be deleted.\n"
          + "  path+ Absolute path of a document or a collection\n"
          + "      to be deleted.\n"
          + "      The contents of a collection is deleted recursively.");

        System.exit(1);
    }

    private static void shutdown(Library lib, LibraryManager libManager)
        throws QizxException
    {
        if (lib.isModified()) {
            lib.rollback();
        }
        lib.close();
        libManager.closeAllLibraries(10000 /*ms*/);
    }

    private static void verbose(String message)
    {
        System.out.println(message);
    }

    private static void warning(String message)
    {
        System.err.println("Warning: " + message);
    }
}
