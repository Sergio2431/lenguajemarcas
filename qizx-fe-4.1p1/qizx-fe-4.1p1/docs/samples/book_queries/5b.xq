(: Lust the title of all books written by authors born in the US. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

for $b in collection("/Books")//t:book
let $a := collection("/Authors")//t:author[t:fullName = $b/t:author]
where $a/t:birthPlace/t:country = "US"
return 
    $b/t:title
