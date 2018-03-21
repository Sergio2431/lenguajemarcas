(: Find all books written by French authors. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

for $a in collection("/Authors")//t:author[@nationality = "France"]
    for $b in collection("/Books")//t:book[.//t:author = $a/t:fullName]
    return 
        $b/t:title
