<xsl:stylesheet
	version="2.0"
	xmlns="http://www.w3.org/1999/xhtml"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:include href="F.xsl"/>
<xsl:include href="O.xsl"/>

<xsl:template match="/">
	<xsl:for-each select="results/dataracelist/datarace">
		<xsl:variable name="te1id" select="@TE1id"/>
		<xsl:variable name="te2id" select="@TE2id"/>
		<xsl:variable name="m_filename"
			select="concat('race_', $te1id, '_', $te2id, '.html')"/>
		<xsl:variable name="l_filename"
			select="concat('path_', $te1id, '.html')"/>
		<xsl:variable name="r_filename"
			select="concat('path_', $te2id, '.html')"/>
		<xsl:result-document href="{$m_filename}">
			<html>
				<head>
					<title>Datarace Details</title>
					<link rel="stylesheet" href="style.css" type="text/css"/>
				</head>
				<body>
					<table class="details">
						<tr><td class="head1" colspan="2">Datarace Details</td></tr>
						<tr><td class="head2" colspan="2">
							Field: <xsl:apply-templates select="id(@Fid)"/></td>
						</tr>
						<tr><td class="head2" colspan="2">
							Object: <xsl:apply-templates select="id(@Oid)"/></td>
						</tr>
						<tr>
							<xsl:copy-of select="document($l_filename)"/>
							<xsl:copy-of select="document($r_filename)"/>
						</tr>
					</table>
				</body>
			</html>
		</xsl:result-document>
	</xsl:for-each>
</xsl:template>

</xsl:stylesheet>

