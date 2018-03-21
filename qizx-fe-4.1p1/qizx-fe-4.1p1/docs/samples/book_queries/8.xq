(: List the pathes of all Documents containing word "alien" and word "sex" or
   containing word "mystery". :)

for $r in collection("/")/*[ft:contains("alien AND sex") or 
                            ft:contains("mystery")]
let $p := xlib:get-property(xlib:document($r), "path")
order by $p descending
return $p;