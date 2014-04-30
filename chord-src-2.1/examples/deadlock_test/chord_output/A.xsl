<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:import href="M.xsl"/>

<xsl:template match="A">
    <xsl:apply-templates select="id(@Mid)"/>
</xsl:template>

</xsl:stylesheet>

