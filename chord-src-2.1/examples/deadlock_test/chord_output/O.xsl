<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:import href="H.xsl"/>

<xsl:template match="O">
	<xsl:text>{</xsl:text>
		<xsl:for-each select="id(@Hids)">
   			<xsl:apply-templates select="."/>
			<xsl:if test="position()!=last()">
				<xsl:text>, </xsl:text>
			</xsl:if>
		</xsl:for-each>
	<xsl:text>}</xsl:text>
</xsl:template>

</xsl:stylesheet>

