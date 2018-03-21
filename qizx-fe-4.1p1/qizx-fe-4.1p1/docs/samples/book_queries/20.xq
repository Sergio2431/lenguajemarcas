(: Find all authors born after 1945 (e.g. Lois McMaster Bujold). :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

collection("/")//t:author[t:birthDate > xs:date("1945-01-01Z")]/t:fullName
