(: List all books containing the value of variable $v:searched 
   in their titles. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";
declare namespace v = "variables";

declare variable $v:searched external;

collection("/")//t:book/t:title[contains(., $v:searched)]
