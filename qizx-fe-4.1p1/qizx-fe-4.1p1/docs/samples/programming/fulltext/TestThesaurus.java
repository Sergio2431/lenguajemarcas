/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package fulltext;

import com.qizx.api.fulltext.TextTokenizer;
import com.qizx.api.fulltext.Thesaurus;

import java.util.HashMap;

/**
 * Simple test implementation of a Thesaurus.
 * Resides in memory, implemented as a prefix tree.
 */
public class TestThesaurus
    implements Thesaurus
{
    TestThesaurus.Node root = new Node();
    TextTokenizer tokenizer;
    // filters:
    String relationship;
    int minLevel = 0, maxLevel = Integer.MAX_VALUE;
    TestThesaurus source;
    
    public TestThesaurus(TextTokenizer tokenizer)
    {
        this.tokenizer = tokenizer;
    }
    
    /**
     * Builds a Thesaurus driver that uses another thesaurus as source.
     * Typically, filters on relationship and level will be specified on the
     * new thesaurus.
     * @param source
     */
    public TestThesaurus(TestThesaurus source)
    {
        this.source = source;
    }
    
    /**
     * Filters entries based on relationship.
     * @param relationship the identifier of a relationship (e.g "NT")
     */
    public void setRelationshipFilter(String relationship)
    {
        this.relationship = relationship;
    }
    
    /**
     * Filters entries based on level.
     * @param min the minimum level (included) of entries that can be retrieved
     * @param max the maximum level (included) of entries that can be retrieved
     */
    public void setLevelFilter(int min, int max)
    {
        this.minLevel = min;
        this.maxLevel = max;
    }
    
    public LookupResult lookup(TokenSequence tokens)
    {
        LookupResult raw = (source == null? this : source).rawLookup(tokens);
        if(raw == null)
            return null;
        // need to filter relationship, levels 
        LookupResult res = new LookupResult(raw.consumedTokens());
        for(int t = 0, siz = raw.size(); t < siz; t++) {
            Synonym syn = raw.getSynonym(t);
            if( (relationship == null || relationship.equals(syn.relationship))
                 && syn.level >= minLevel && syn.level <= maxLevel)
                res.addSynonym(syn);
        }        
        return res;
    }
    
    protected LookupResult rawLookup(TokenSequence tokens)
    {
        TestThesaurus.Node res = (TestThesaurus.Node) root.child(tokens.getTokenAt(0));
        if(res == null)
            return null;
        // find the longest
        TestThesaurus.Node match = null;
        int ntoken = tokens.size();
        for(int t = 1; t < ntoken; ++t) {
            if(res.size() > 0)
                match = res;    // actual result
            res = res.child(tokens.getTokenAt(t));
            if(res == null)
                break;
        }
        if(res != null && res.size() > 0)
            match = res;    // actual result
        return match;        
    }

    /**
     * Adds an entry to the Thesaurus.
     * @param entry one or several words defining the entry
     * @param synonym a synonym for the entry (one or several words)
     * @param relationship the name of a relationship (e.g "NT")
     * @param level level of the entry for the relationship (typically 1)
     */
    public void defineEntry(String entry, String synonym, 
                            String relationship, int level)
    {
        
        TokenSequence entrySeq = split(entry);
        TestThesaurus.Node node = root;
        for(int t = 0; t < entrySeq.size(); t++)
            node = node.addBranch(entrySeq.getTokenAt(t));
        if(node.size() == 0)
            node.addSynonym(entrySeq, relationship, level); // self necessary
        node.addSynonym(split(synonym), relationship, level);
        node.setConsumedTokens(entrySeq.size());
    }

    /**
     * Adds a set of entries for a symmetric relationship.
     * @param entries an array of entries (one or several words for each entry)
     * @param relationship the name of a relationship (e.g "NT")
     * @param level level of the entry for the relationship (typically 1)
     */
    public void defineEquivalences(String[] entries,
                                   String relationship, int level)
    {
        TokenSequence[] tseqs = new TokenSequence[entries.length];
        for (int i = 0; i < entries.length; i++) {
            tseqs[i] = split(entries[i]);                
        }
        for (int i = 0; i < entries.length; i++) {
            TokenSequence ts = tseqs[i];
            TestThesaurus.Node node = root;
            for(int t = 0; t < ts.size(); t++)
                node = node.addBranch(ts.getTokenAt(t));
            for (int i2 = 0; i2 < entries.length; i2++)
                node.addSynonym(tseqs[i2], relationship, level);
            node.setConsumedTokens(ts.size());
        }           
    }

    private TokenSequence split(String s)
    {
        tokenizer.start(s);
        TokenSequence seq = new TokenSequence();
        for (; tokenizer.nextToken() == TextTokenizer.WORD; ) {
            seq.addToken(tokenizer.getTokenChars());
        }
        return seq;
    }
    
    static class Node extends LookupResult
    {
        HashMap map;
        
        public Node()
        {
            super(0);
        }
        
        public void setConsumedTokens(int size)
        {
            consumedTokens = size;
        }

        public TestThesaurus.Node child(char[] token)
        {
            if(map == null)
                return null;
            return (TestThesaurus.Node) map.get(new String(token));   // OOPS
        }

        public TestThesaurus.Node addBranch(char[] token)
        {
            if(map == null)
                map = new HashMap();
            TestThesaurus.Node node = child(token);
            if(node == null) {
                node = new Node();
                map.put(new String(token), node);  // OOPS
            }
            return node;
        }
    }
}
