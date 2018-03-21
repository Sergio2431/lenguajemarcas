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
/*
 *    Qizx 4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx 4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx 4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx 4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx 4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 *    Qizx 4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package reindex;

import java.io.IOException;
import java.io.File;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import com.qizx.api.QizxException;
import com.qizx.api.Indexing;
import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.LibraryManagerFactory;

public class ReIndex {
    public static void main(String[] args) 
        throws IOException, SAXException, QizxException {
        if (args.length != 3) {
            usage();
        }
        File storageDir = new File(args[0]);
        String libName = args[1];
        File indexingFile = new File(args[2]);

        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        LibraryManager libManager = factory.openLibraryGroup(storageDir);
        Library lib = libManager.openLibrary(libName);

        try {
            verbose("Loading indexing specifications from '" + 
                    indexingFile + "'...");
            Indexing indexing = loadIndexing(indexingFile);
            lib.setIndexing(indexing);

            verbose ("Re-indexing library '" + libName + "'...");
            lib.reIndex();
        } finally {
            shutdown(lib, libManager);
        }
    }

    private static void usage() {
        System.err.println(
          "usage: java ReIndex libraries_storage_dir library_name" +
          " indexing_spec\n" +
          "  libraries_storage_dir Directory containing libraries.\n" +
          "  library_name Name of library being queried.\n" +
          "  indexing_spec File containing the indexing specification.");
        System.exit(1);
    }

    private static Indexing loadIndexing(File file) 
        throws IOException, SAXException, QizxException {
        Indexing indexing = new Indexing();

        String systemId = file.toURI().toASCIIString();
        indexing.parse(new InputSource(systemId));

        return indexing;
    }

    private static void shutdown(Library lib, LibraryManager libManager) 
        throws QizxException {
        lib.close();
        libManager.closeAllLibraries(10000 /*ms*/);
    }

    private static void verbose(String message) {
        System.out.println(message);
    }
}
