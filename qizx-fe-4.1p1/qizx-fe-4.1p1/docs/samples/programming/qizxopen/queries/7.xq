(: List the paths of all author blurbs containing 
   the words "alien" and "sex". :)
declare namespace html = "http://www.w3.org/1999/xhtml";

import module namespace col = "collections.xqm"; 

for $b in $col:AuthorBlurbs//html:body[ft:contains("alien AND sex")]
return
    base-uri($b)

