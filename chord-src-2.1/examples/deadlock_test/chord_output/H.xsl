<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:template match="H">
    <xsl:variable name="file" select="@file"/>
    <xsl:variable name="line" select="@line"/>
    <a href="{$file}.html#{$line}">
		<xsl:for-each select="tokenize(@type, '\.')">
			<xsl:value-of select="."/>
			<xsl:if test="position()!=last()">.<wbr/></xsl:if>
		</xsl:for-each>
	</a>
</xsl:template>

</xsl:stylesheet>

