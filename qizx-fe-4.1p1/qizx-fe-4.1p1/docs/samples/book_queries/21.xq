(: Find all books published before 1960 (e.g. The Caves of Steel). :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

collection("/")//t:book[t:publicationDate < 1960]/t:title
