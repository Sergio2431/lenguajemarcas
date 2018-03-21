(: List all books containing the value of variable $searched 
   in their titles. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

declare variable $searched external;

collection("/Books")//t:book/t:title[contains(., $searched)]
