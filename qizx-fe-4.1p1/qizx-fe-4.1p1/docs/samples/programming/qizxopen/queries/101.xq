(: List all books containing the value of variable $searched 
   in their titles. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

import module namespace col = "collections.xqm"; 

declare variable $searched external;

$col:books//t:book/t:title[contains(., $searched)]
