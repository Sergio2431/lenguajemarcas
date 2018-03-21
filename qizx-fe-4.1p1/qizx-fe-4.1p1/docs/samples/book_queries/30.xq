(: List the original, Wikipedia, URLs of author blurbs containing 
   word "Russian" and copied locally after September 15, 2007. :)
declare namespace html = "http://www.w3.org/1999/xhtml";

for $doc in xlib:query-properties("/Author Blurbs/*.xhtml",
                                  copyDate ge xs:date("2007-09-15"))
where $doc/*[ft:contains("Russian")]
return xlib:get-property($doc, "copiedURL")