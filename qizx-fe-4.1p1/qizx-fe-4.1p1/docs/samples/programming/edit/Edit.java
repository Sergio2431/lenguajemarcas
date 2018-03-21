/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package edit;

import java.io.File;
import org.w3c.dom.Document;
import com.qizx.api.QizxException;
import com.qizx.api.Item;
import com.qizx.api.ItemSequence;
import com.qizx.api.Node;
import com.qizx.api.Collection;
import com.qizx.api.Expression;
import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.LibraryManagerFactory;
import com.qizx.api.XMLPushStream;
import com.qizx.api.util.DOMToPushStream;

public class Edit
{
    public static void main(String[] args) 
        throws QizxException {
        if (args.length != 5) {
            usage();
            /*NOTREACHED*/
        }
        File storageDir = new File(args[0]);
        String libName = args[1];
        String collectionPath = args[2];
        String authorName = args[3];
        String pseudonym = args[4];
        
        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        LibraryManager libManager = factory.openLibraryGroup(storageDir);
        Library lib = libManager.openLibrary(libName);

        try {
            addPseudo(lib, collectionPath, authorName, pseudonym);

            verbose("Committing changes...");
            lib.commit();
        } finally {
            shutdown(lib, libManager);
        }
    }

    private static void usage() {
        System.err.println(
          "usage: java Edit libraries_storage_dir library_name" +
          " collection_path author_name pseudonym\n" +
          "  libraries_storage_dir Directory containing libraries.\n" +
          "  library_name Name of library containing documents\n" +
          "      and collections to be deleted.\n" +
          "  collection_path Absolute path of collection containing\n" +
          "      authors.\n" +
          "  author_name Full name of author for which a pseudonym\n" + 
          "      is to be added.\n" +
          "  pseudonym pseudonym is to be added.");

        System.exit(1);
    }

    private static void addPseudo(Library lib, String collectionPath,
                                  String authorName, String pseudonym) 
        throws QizxException {
        Node author = findAuthor(lib, collectionPath, authorName);
        if (author == null)
            return;

        if (hasPseudonym(author, pseudonym)) {
            warning("'" + authorName + "' already has pseudonym '" + 
                    pseudonym + "'");
            return;
        }

        Document doc = (Document) author.getDocumentNode().getObject();
        if (!doAddPseudo(doc, pseudonym))
            return;

        XMLPushStream out = 
            lib.beginImportDocument(author.getLibraryDocument().getPath());

        DOMToPushStream helper = new DOMToPushStream(lib, out);
        helper.putDocument(doc);
        lib.endImportDocument();
    }
    
    private static final String TUTORIAL_NS_URI = 
        "http://www.qizx.com/namespace/Tutorial";

    private static Node findAuthor(Library lib, String collectionPath,
                                   String authorName) 
        throws QizxException {
        Collection collection = lib.getCollection(collectionPath);
        if (collection == null) {
            error("'" + collectionPath + "' is not a collection");
            return null;
        }

        String script = 
            "declare namespace t = '" + TUTORIAL_NS_URI + "';\n" +
            "declare variable $name external;\n" +
            "/t:author[t:fullName = $name]";

        Expression expr = lib.compileExpression(script);
        expr.bindImplicitCollection(collection);
        expr.bindVariable(lib.getQName("name"), authorName, /*type*/ null);

        ItemSequence items = expr.evaluate();
        if (!items.moveToNextItem()) {
            error("Don't find author '" + authorName + "'");
            return null;
        }
        Item item = items.getCurrentItem();

        return item.getNode();
    }

    private static boolean hasPseudonym(Node element, String pseudonym) 
        throws QizxException {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.isElement()) {
                String childName = child.getNodeName().getLocalPart();
                if ("pseudonyms".equals(childName)) {
                    return hasPseudonym(child, pseudonym);
                } else if ("pseudonym".equals(childName)) {
                    if (pseudonym.equals(child.getStringValue())) {
                        return true;
                    }
                }
            }

            child = child.getNextSibling();
        }

        return false;
    }

    private static boolean doAddPseudo(Document doc, String pseudonym) {
        org.w3c.dom.Node author = doc.getFirstChild();
        org.w3c.dom.Node fullName = null;
        org.w3c.dom.Node pseudonyms = null;
        String existingPseudonym = null;

        org.w3c.dom.Node child = author.getFirstChild();
        while (child != null) {
            String childName = child.getLocalName();

            if ("fullName".equals(childName)) {
                fullName = child;
            } else if ("pseudonyms".equals(childName)) {
                pseudonyms = child;
                break;
            } else if ("pseudonym".equals(childName)) {
                existingPseudonym = getTextContent((org.w3c.dom.Element)child);
                // Will be replaced by a t:pseudonyms element.
                author.removeChild(child);
                break;
            }

            child = child.getNextSibling();
        }
        
        if (fullName == null) {
            error("invalid t:author element:" +
                  " don't find t:fullName child element");
            return false;
        }

        if (pseudonyms == null) {
            pseudonyms = doc.createElementNS(TUTORIAL_NS_URI, "t:pseudonyms");

            if (existingPseudonym != null) {
                org.w3c.dom.Node existingPseudo = 
                    doc.createElementNS(TUTORIAL_NS_URI, "t:pseudonym");
                existingPseudo.appendChild(
                    doc.createTextNode(existingPseudonym));

                pseudonyms.appendChild(existingPseudo);
            }

            org.w3c.dom.Node newPseudo = 
                doc.createElementNS(TUTORIAL_NS_URI, "t:pseudonym");
            newPseudo.appendChild(doc.createTextNode(pseudonym));

            pseudonyms.appendChild(newPseudo);

            author.insertBefore(fullName.getNextSibling(), pseudonyms);
        } else {
            org.w3c.dom.Node newPseudo = 
                doc.createElementNS(TUTORIAL_NS_URI, "t:pseudonym");
            newPseudo.appendChild(doc.createTextNode(pseudonym));

            pseudonyms.appendChild(newPseudo);
        }

        return true;
    }

    private static String getTextContent(org.w3c.dom.Element element) {
        StringBuffer buffer = new StringBuffer();
        getTextContent(element, buffer);
        return buffer.toString();
    }

    private static void getTextContent(org.w3c.dom.Element element,
                                       StringBuffer buffer) {
        org.w3c.dom.Node child = element.getFirstChild();
        while (child != null) {
            switch (child.getNodeType()) {
            case org.w3c.dom.Node.TEXT_NODE:
            case org.w3c.dom.Node.CDATA_SECTION_NODE:
                buffer.append(child.getNodeValue());
                break;
            case org.w3c.dom.Node.ELEMENT_NODE:
                getTextContent((org.w3c.dom.Element) child, buffer);
                break;
            }

            child = child.getNextSibling();
        }
    }

    private static void shutdown(Library lib, LibraryManager libManager) 
        throws QizxException {
        if (lib.isModified()) {
            lib.rollback();
        }
        lib.close();
        libManager.closeAllLibraries(10000 /*ms*/);
    }

    private static void error(String message) {
        System.err.println("Error: " + message);
    }

    private static void warning(String message) {
        System.err.println("Warning: " + message);
    }

    private static void verbose(String message) {
        System.out.println(message);
    }
}
