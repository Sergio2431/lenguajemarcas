declare variable $src-member external;
declare variable $dst-member external;
try {
  xlib:rename-member($src-member, $dst-member),
  xlib:commit()
}
catch($err) {
 element error { $err }
}
