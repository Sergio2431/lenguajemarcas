declare default element namespace 'http://www.qizx.com/namespace/Tutorial';

declare variable $ERR := qName('http://www.w3.org/2005/xqt-errors', 'ERR00001');

declare variable $authorName external;
declare variable $pseudo external;

let $auth := /author[fullName = $authorName]
return
 if (empty($auth))
   then error($ERR, <t>no such author {$authorName}</t>)
 else if ($auth/pseudonyms[pseudonym = $pseudo]) 
   then error($ERR, <t>{$authorName} already has pseudonym '{$pseudo}'</t>)
 else if ($auth/pseudonyms)
   then insert node <pseudonym>{ $pseudo }</pseudonym> 
          into $auth/pseudonyms
   else insert node <pseudonyms><pseudonym>{ $pseudo }</pseudonym></pseudonyms> 
          into $auth
          