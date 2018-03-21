<?xml version='1.0' ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xt="http://www.jclark.com/xt"
                extension-element-prefixes="xt" version="2.0">

<xsl:output method="html" indent="yes"
            doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"
            doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN"/>
 
<xsl:template match="/">
  <html><head>
    <style >
      h2.title { color: #505080; }
      span.duration { font-size: 0.7em; color: #505060; }
      span.title { font-weight: bold; font-style: italic; color: #a0a0a0; }
    </style>
  </head>
<body>
<xsl:apply-templates select="disc"/>
</body>
</html>
</xsl:template>

<xsl:template match="disc">
<h2 class='title'><xsl:value-of select="title"/></h2>
<ol>
    <xsl:apply-templates select="track"/>
</ol>
<p class='properties'> Total duration <xsl:call-template name="duration"><xsl:with-param name="dur"><xsl:value-of select="@disc-length"/></xsl:with-param></xsl:call-template></p>
</xsl:template>

<xsl:template match="track">
<li class='track'>
 <span class='title'><xsl:apply-templates select="title" /></span>
 <span class='duration'> - <xsl:call-template name="duration"><xsl:with-param name="dur"><xsl:value-of select="@duration"/></xsl:with-param></xsl:call-template></span>
</li>
</xsl:template>


<xsl:template name="duration">
  <xsl:param name="dur"/>
  <xsl:value-of select="format-number($dur div 60, '0')"/>'<xsl:value-of select="format-number($dur mod 60, '00')"/>''
</xsl:template>

</xsl:stylesheet>
