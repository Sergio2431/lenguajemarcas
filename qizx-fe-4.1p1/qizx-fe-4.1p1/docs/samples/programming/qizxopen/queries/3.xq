(: List all books by their titles. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

import module namespace col = "collections.xqm"; 

$col:books//t:book/t:title
