(: List the name of authors whose record contains the word "alien" 
   and for which this word is found in an HTML paragraph element. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";
declare namespace html = "http://www.w3.org/1999/xhtml";

collection("/Authors")/t:author[.//html:p contains text "alien"]//t:fullName
