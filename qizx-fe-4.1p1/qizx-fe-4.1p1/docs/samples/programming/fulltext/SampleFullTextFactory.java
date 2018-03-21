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
/*
 *    Qizx Free_Engine-4.0
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package fulltext;

import com.qizx.api.fulltext.FullTextFactory;
import com.qizx.api.fulltext.Scorer;
import com.qizx.api.fulltext.Stemmer;
import com.qizx.api.fulltext.TextTokenizer;
import com.qizx.api.fulltext.Thesaurus;
import com.qizx.api.util.fulltext.DefaultScorer;
import com.qizx.api.util.fulltext.DefaultTextTokenizer;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.*;

import java.util.HashMap;

/**
 * An example of FullTextFactory extending the default implementation.
 * <p>
 * Provides an access to Snowball stemmers and to a simple Thesaurus
 * implementation.
 */
public class SampleFullTextFactory
    implements FullTextFactory
{
    private static HashMap snowballStemmers;
    private TestThesaurus defaultThesaurus =
        new TestThesaurus(new DefaultTextTokenizer());
    private HashMap thesauri = new HashMap();
    private String defaultLanguage;
    
    public SampleFullTextFactory()
    {
    }
    
    public SampleFullTextFactory(String defaultLanguage)
    {
        this.defaultLanguage = defaultLanguage;
    }
    
    // ------------ interface FullTextFactory -------------------------------
    
    public TextTokenizer getTokenizer(String languageCode)
    {
        if(languageCode != null) {
            if(languageCode.startsWith("zh"))
                return null;
        }
        
        return new DefaultTextTokenizer();
    }

    public Stemmer getStemmer(String languageCode)
    {
        if(languageCode == null)
            languageCode = defaultLanguage;
        
        if(languageCode.length() < 2)
            return null;
        String prefix = languageCode.substring(0, 2);
        fetchSnowballStemmers();
        if(snowballStemmers != null) {
            Class sclass = (Class) snowballStemmers.get(prefix);
            if(sclass != null) {
                try {
                    return new SnowballWrapper((SnowballStemmer) sclass.newInstance());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public Thesaurus getThesaurus(String uri, String languageCode,
                                  String relationship,
                                  int levelMin, int levelMax)
    {
	    // In principle, languageCode should be used when looking for a thesaurus
        TestThesaurus thesaurus = ("default".equals(uri))? defaultThesaurus
                                             : (TestThesaurus) thesauri.get(uri);
        if(thesaurus == null)
            return null;
        thesaurus = new TestThesaurus(thesaurus);
        thesaurus.setRelationshipFilter(relationship);
        thesaurus.setLevelFilter(levelMin, levelMax);
        return thesaurus;
    }

    public TestThesaurus addThesaurus(String uri)
    {
        TestThesaurus thesaurus = (TestThesaurus) thesauri.get(uri);
        if(thesaurus == null) {
            thesaurus = new TestThesaurus(new DefaultTextTokenizer());
            thesauri.put(uri, thesaurus);
        }
        return thesaurus;
    }

    public void setDefaultThesaurus(TestThesaurus th)
    {
        defaultThesaurus = th;
    }

    public Scorer createScorer()
    {
        return new DefaultScorer();
    }

    // ------------- stemming ---------------------------------------------

    public static class SnowballWrapper
        implements Stemmer
    {
        SnowballStemmer stemmer;
        char[] tmpToken = new char[100];
    
        public SnowballWrapper(SnowballStemmer stemmer)
        {
            this.stemmer = stemmer;
        }

        public char[] stem(char[] token)
        {
            int tokLen = token.length;
            if(tokLen > tmpToken.length)
                tmpToken = new char[tokLen + 20];
            for(int i = tokLen; --i >= 0; )
                tmpToken[i] = Character.toLowerCase(token[i]);
            stemmer.setCurrent(new String(tmpToken, 0, tokLen));
            if(!stemmer.stem())
                return token;
            String result = stemmer.getCurrent();
            return result.toCharArray();
        }    
    }
    
    private static synchronized void fetchSnowballStemmers()
    {
        try {
            if(snowballStemmers == null) {
                snowballStemmers = new HashMap();
                snowballStemmers.put("dk", danishStemmer.class);
                snowballStemmers.put("nl", dutchStemmer.class);
                snowballStemmers.put("en", englishStemmer.class);
                snowballStemmers.put("fi", finnishStemmer.class);
                snowballStemmers.put("fr", frenchStemmer.class);
                snowballStemmers.put("de", germanStemmer.class);
                snowballStemmers.put("hu", hungarianStemmer.class);
                snowballStemmers.put("it", italianStemmer.class);
                snowballStemmers.put("no", norwegianStemmer.class);
                snowballStemmers.put("pt", portugueseStemmer.class);
                snowballStemmers.put("ro", romanianStemmer.class);
                snowballStemmers.put("ru", russianStemmer.class);
                snowballStemmers.put("es", spanishStemmer.class);
                snowballStemmers.put("sv", swedishStemmer.class);
                snowballStemmers.put("tr", turkishStemmer.class);
            }
        }
        catch(java.lang.NoClassDefFoundError ignored) { ; } // ignore
    }
}
