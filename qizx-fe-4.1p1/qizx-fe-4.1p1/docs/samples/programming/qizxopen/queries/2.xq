(: Create and return an author. :)
declare namespace t = "http://www.qizx.com/namespace/Tutorial";

element t:author { 
   attribute nationality { "France" }, 
   attribute gender { "male" }, 
   element t:fullName { "Jules Vernes" },
   element t:birthDate { "February 8, 1828" },
   element t:birthPlace { 
      element t:city { "Nantes" }, 
      element t:country { "France" }
   }
}
