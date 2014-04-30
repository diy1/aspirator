<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:import href="H.xsl"/>
<xsl:import href="I.xsl"/>

<xsl:template match="C">
    <xsl:text>[</xsl:text>
		<xsl:choose>
			<xsl:when test="@ids">
				<xsl:for-each select="id(@ids)">
    				<xsl:apply-templates select="."/>
					<xsl:if test="position()!=last()">
						::<wbr/>
					</xsl:if>
				</xsl:for-each>
			</xsl:when>
			<xsl:otherwise>
				<xsl:text>main</xsl:text>
			</xsl:otherwise>
		</xsl:choose>
    <xsl:text>]</xsl:text>
</xsl:template>

</xsl:stylesheet>

