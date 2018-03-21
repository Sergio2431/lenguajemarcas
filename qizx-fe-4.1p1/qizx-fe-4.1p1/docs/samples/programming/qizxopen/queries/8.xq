(: List the pathes of all Documents containing word "alien" and word "sex" or
   containing word "mystery". :)

import module namespace col = "collections.xqm"; 

for $r in $col:AuthorBlurbs/*[ft:contains("alien AND sex") or 
                             ft:contains("mystery")]
let $p := base-uri($r)
order by $p descending
return $p;