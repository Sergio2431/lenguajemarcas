module namespace co = "collections.xqm"; 

declare variable $co:authors := collection("../../book_data/Authors/*.xml");
declare variable $co:books := collection("../../book_data/Books/*.xml");
declare variable $co:AuthorBlurbs :=
	 collection("../../book_data/Author Blurbs/*.xhtml")