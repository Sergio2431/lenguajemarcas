(: Find all books written by authors born in the US. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

import module namespace col = "collections.xqm"; 

<t:books>
{
for $a in $col:authors//t:author,
    $b in $col:books//t:book
where $b//t:author = $a/t:fullName and $a/t:birthPlace/t:country = "US"
order by $a/t:fullName
return 
    <t:book>
    {
      $b/t:title,
      $a/t:fullName
    }
    </t:book>
}
</t:books>
