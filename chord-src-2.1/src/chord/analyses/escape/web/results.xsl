<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:include href="E.xsl"/>
<xsl:include href="H.xsl"/>

<xsl:template match="/">
    <xsl:result-document href="results.html">
	<html>
		<head>Thread-Escape Results</head>
		<body>
		<table border="1">
			<xsl:for-each select="results/escapelist/*">
				<tr>
                    <xsl:variable name="color">
                        <xsl:choose>
                            <xsl:when test="name() = 'pathEsc'">yellow</xsl:when>
                            <xsl:when test="name() = 'fullEsc'">red</xsl:when>
                            <xsl:otherwise>green</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
					<td bgcolor="{$color}"><xsl:value-of select="name()"/></td>
					<td><xsl:apply-templates select="id(@Eid)"/></td>
					<td>
						<xsl:for-each select="id(@Hids)">
							<xsl:apply-templates select="."/>
							<xsl:if test="position()!=last()">
								<xsl:text>, </xsl:text>
							</xsl:if>
						</xsl:for-each>
					</td>
				</tr>
			</xsl:for-each>
		</table>
		</body>
	</html>
    </xsl:result-document>
</xsl:template>


</xsl:stylesheet>

