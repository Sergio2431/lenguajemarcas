(: Find all books written by authors born in the US. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

<t:books>
{
for $a in collection("/Authors")//t:author,
    $b in collection("/Books")//t:book
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
