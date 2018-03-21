(: List the paths of all author blurbs containing 
   the words "alien" and "sex". :)
declare namespace html = "http://www.w3.org/1999/xhtml";

for $b in collection("/Author Blurbs")//html:body[ft:contains("alien AND sex")]
return
    xlib:get-property(xlib:document($b), "path")

