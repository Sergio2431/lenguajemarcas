(: Find all books written by French authors. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

  (: import module namespace col = "collections.xqm"; :)
declare variable $authors := collection("../../book_data/Authors/*.xml");
declare variable $books := collection("../../book_data/Books/*.xml");

for $a in $authors//t:author[@nationality = "France"]
    for $b in $books//t:book[.//t:author = $a/t:fullName]
    return 
        $b/t:title
