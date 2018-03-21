(: List the name of authors whose record contains the word "alien" 
   and for which this word is found in an HTML paragraph element. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";
declare namespace html = "http://www.w3.org/1999/xhtml";

import module namespace col = "collections.xqm"; 

$col:authors/t:author[ft:contains("alien", .//html:p)]//t:fullName
