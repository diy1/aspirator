<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="M">
	<xsl:variable name="file" select="@file"/>
	<xsl:variable name="line" select="@line"/>
	<a href="{$file}.html#{$line}">
		<xsl:value-of select="@sign"/>
	</a>
</xsl:template>

</xsl:stylesheet>

